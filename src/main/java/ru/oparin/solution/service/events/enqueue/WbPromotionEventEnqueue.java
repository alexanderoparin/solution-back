package ru.oparin.solution.service.events.enqueue;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.CabinetTokenType;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.model.WbApiEventType;
import ru.oparin.solution.repository.WbApiEventRepository;
import ru.oparin.solution.service.events.WbApiEventExecutors;
import ru.oparin.solution.service.events.WbApiEventQueueService;
import ru.oparin.solution.service.events.WbApiEventWriter;
import ru.oparin.solution.service.events.payload.*;
import ru.oparin.solution.service.sync.WbPromotionCampaignSyncService;
import ru.oparin.solution.service.sync.WbPromotionCampaignSyncService.StatisticsSyncIdGroup;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Волна рекламы WB: count → adverts → stats → normquery, плюс start/pause РК.
 */
@Service
@RequiredArgsConstructor
public class WbPromotionEventEnqueue {

    private static final int MAX_ATTEMPTS = 5;
    private static final int PRIORITY = 85;
    private static final int STATS_PRIORITY_ACTIVE = 87;
    private static final int STATS_PRIORITY_PAUSED = 86;
    private static final int STATS_PRIORITY_REST = 85;
    private static final int CONTROL_PRIORITY = 95;

    private final WbApiEventWriter writer;
    private final WbApiEventRepository eventRepository;
    private final WbApiEventQueueService queueService;
    private final WbPromotionCampaignSyncService promotionCampaignSyncService;

    /**
     * Поставить в очередь запуск рекламной кампании.
     *
     * @return id созданного события или {@code null}, если задача уже в очереди
     */
    @Transactional
    public Long enqueueWbPromotionCampaignStart(Long cabinetId, Long advertId, String triggerSource) {
        return enqueueWbPromotionCampaignControl(
                cabinetId,
                advertId,
                WbApiEventType.PROMOTION_CAMPAIGN_START,
                WbApiEventExecutors.PROMOTION_CAMPAIGN_START,
                "PROMOTION_START",
                triggerSource
        );
    }

    /**
     * Поставить в очередь паузу рекламной кампании.
     *
     * @return id созданного события или {@code null}, если задача уже в очереди
     */
    @Transactional
    public Long enqueueWbPromotionCampaignPause(Long cabinetId, Long advertId, String triggerSource) {
        return enqueueWbPromotionCampaignControl(
                cabinetId,
                advertId,
                WbApiEventType.PROMOTION_CAMPAIGN_PAUSE,
                WbApiEventExecutors.PROMOTION_CAMPAIGN_PAUSE,
                "PROMOTION_PAUSE",
                triggerSource
        );
    }

    /**
     * @return {@code true}, если создано новое событие PROMOTION_COUNT
     */
    @Transactional
    public boolean enqueuePromotionRequestLevelEvents(Long cabinetId, WbMainStepPayload payload, String triggerSource) {
        String dedupKey = "PROMOTION_COUNT:" + promotionPeriodKey(cabinetId, payload.dateFrom(), payload.dateTo());
        return writer.insertIfAbsent(
                cabinetId,
                WbApiEventType.PROMOTION_COUNT,
                WbApiEventExecutors.PROMOTION_COUNT,
                payload,
                dedupKey,
                MAX_ATTEMPTS,
                PRIORITY,
                triggerSource,
                null
        ).isPresent();
    }

    /**
     * Батчи advert_id для загрузки карточек РК.
     */
    @Transactional
    public void enqueuePromotionAdvertsBatchEvents(
            Long cabinetId,
            WbMainStepPayload payload,
            List<Long> campaignIds,
            String triggerSource
    ) {
        Cabinet cabinet = writer.requireCabinet(cabinetId);
        int size = promotionCampaignSyncService.getCampaignsBatchSize(
                cabinet.getTokenType() != null ? cabinet.getTokenType() : CabinetTokenType.BASIC);
        for (int i = 0, batchIndex = 0; i < campaignIds.size(); i += size, batchIndex++) {
            int end = Math.min(i + size, campaignIds.size());
            List<Long> batch = campaignIds.subList(i, end);
            enqueuePromotionAdvertsBatchEvent(cabinet, batch, batchIndex, payload, triggerSource);
        }
    }

    /**
     * После завершения advert-батчей ставит fullstats, если нет активных advert-батчей.
     */
    @Transactional
    public void schedulePromotionStatsAfterAdvertsIfReady(
            Long cabinetId,
            WbMainStepPayload payload,
            String triggerSource,
            long excludeAdvertBatchEventId
    ) {
        String advertsPrefix = promotionAdvertsDedupPrefix(cabinetId, payload.dateFrom(), payload.dateTo());
        if (eventRepository.existsOtherByCabinet_IdAndEventTypeAndStatusInAndDedupKeyPrefix(
                cabinetId,
                WbApiEventType.PROMOTION_ADVERTS_BATCH,
                WbApiEventWriter.ACTIVE_STATUSES,
                advertsPrefix,
                excludeAdvertBatchEventId
        )) {
            return;
        }
        List<StatisticsSyncIdGroup> groups = promotionCampaignSyncService.listCampaignIdGroupsForStatisticsSync(
                cabinetId);
        if (groups.isEmpty()) {
            queueService.tryFinalizeMain(cabinetId, excludeAdvertBatchEventId);
            return;
        }
        String statsPrefix = promotionStatsDedupPrefix(cabinetId, payload.dateFrom(), payload.dateTo());
        if (eventRepository.existsByCabinet_IdAndEventTypeAndStatusInAndDedupKeyPrefix(
                cabinetId,
                WbApiEventType.PROMOTION_STATS_BATCH,
                WbApiEventWriter.ACTIVE_STATUSES,
                statsPrefix
        )) {
            return;
        }
        Cabinet cabinet = writer.requireCabinet(cabinetId);
        int statBatchSize = promotionCampaignSyncService.getStatisticsBatchSize(
                cabinet.getTokenType() != null ? cabinet.getTokenType() : CabinetTokenType.BASIC);
        int batchIndex = 0;
        for (StatisticsSyncIdGroup group : groups) {
            int priority = statsBatchPriority(group.rank());
            for (List<Long> batch : chunk(group.advertIds(), statBatchSize)) {
                enqueuePromotionStatsBatchEvent(cabinet, batch, batchIndex, payload, triggerSource, priority);
                batchIndex++;
            }
        }
    }

    /**
     * Есть ли другие активные батчи fullstats за период.
     */
    @Transactional(readOnly = true)
    public boolean hasOtherActivePromotionStatsBatches(
            Long cabinetId,
            Long excludeEventId,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        return eventRepository.existsOtherByCabinet_IdAndEventTypeAndStatusInAndDedupKeyPrefix(
                cabinetId,
                WbApiEventType.PROMOTION_STATS_BATCH,
                WbApiEventWriter.ACTIVE_STATUSES,
                promotionStatsDedupPrefix(cabinetId, dateFrom, dateTo),
                excludeEventId
        );
    }

    /**
     * После завершения всех батчей fullstats ставит в очередь загрузку normquery stats.
     */
    @Transactional
    public void schedulePromotionNormQueryStatsIfReady(
            Long cabinetId,
            LocalDate dateFrom,
            LocalDate dateTo,
            boolean includeStocks,
            String triggerSource,
            long excludeStatsBatchEventId
    ) {
        if (eventRepository.existsOtherByCabinet_IdAndEventTypeAndStatusInAndDedupKeyPrefix(
                cabinetId,
                WbApiEventType.PROMOTION_STATS_BATCH,
                WbApiEventWriter.ACTIVE_STATUSES,
                promotionStatsDedupPrefix(cabinetId, dateFrom, dateTo),
                excludeStatsBatchEventId
        )) {
            return;
        }
        List<StatisticsSyncIdGroup> groups = promotionCampaignSyncService.listCampaignIdGroupsForStatisticsSync(
                cabinetId);
        if (groups.isEmpty()) {
            queueService.tryFinalizeMain(cabinetId, excludeStatsBatchEventId);
            return;
        }
        String normqueryPrefix = promotionNormQueryStatsDedupPrefix(cabinetId, dateFrom, dateTo);
        if (eventRepository.existsByCabinet_IdAndEventTypeAndStatusInAndDedupKeyPrefix(
                cabinetId,
                WbApiEventType.PROMOTION_NORMQUERY_STATS_BATCH,
                WbApiEventWriter.ACTIVE_STATUSES,
                normqueryPrefix
        )) {
            return;
        }
        Cabinet cabinet = writer.requireCabinet(cabinetId);
        int batchSize = promotionCampaignSyncService.getNormqueryCampaignsBatchSize(
                cabinet.getTokenType() != null ? cabinet.getTokenType() : CabinetTokenType.BASIC);
        WbMainStepPayload payload = WbMainStepPayload.builder()
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .includeStocks(includeStocks)
                .build();
        int batchIndex = 0;
        for (StatisticsSyncIdGroup group : groups) {
            int priority = statsBatchPriority(group.rank());
            for (List<Long> batch : chunk(group.advertIds(), batchSize)) {
                enqueuePromotionNormQueryStatsBatchEvent(cabinet, batch, batchIndex, payload, triggerSource, priority);
                batchIndex++;
            }
        }
    }

    /**
     * Есть ли другие активные батчи normquery stats за период.
     */
    @Transactional(readOnly = true)
    public boolean hasOtherActivePromotionNormQueryStatsBatches(
            Long cabinetId,
            Long excludeEventId,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        return eventRepository.existsOtherByCabinet_IdAndEventTypeAndStatusInAndDedupKeyPrefix(
                cabinetId,
                WbApiEventType.PROMOTION_NORMQUERY_STATS_BATCH,
                WbApiEventWriter.ACTIVE_STATUSES,
                promotionNormQueryStatsDedupPrefix(cabinetId, dateFrom, dateTo),
                excludeEventId
        );
    }

    /**
     * Активный запуск РК в очереди.
     */
    @Transactional(readOnly = true)
    public boolean hasActiveWbPromotionCampaignStart(Long cabinetId, Long advertId) {
        return hasActivePromotionControl(cabinetId, advertId, "PROMOTION_START");
    }

    /**
     * Активная пауза РК в очереди.
     */
    @Transactional(readOnly = true)
    public boolean hasActiveWbPromotionCampaignPause(Long cabinetId, Long advertId) {
        return hasActivePromotionControl(cabinetId, advertId, "PROMOTION_PAUSE");
    }

    private Long enqueueWbPromotionCampaignControl(
            Long cabinetId,
            Long advertId,
            WbApiEventType eventType,
            String executorBean,
            String dedupPrefix,
            String triggerSource
    ) {
        String dedupKey = dedupPrefix + ":" + cabinetId + ":" + advertId;
        WbPromotionCampaignControlPayload payload = WbPromotionCampaignControlPayload.builder()
                .advertId(advertId)
                .build();
        return writer.insertIfAbsent(
                cabinetId,
                eventType,
                executorBean,
                payload,
                dedupKey,
                MAX_ATTEMPTS,
                CONTROL_PRIORITY,
                triggerSource,
                null
        ).map(WbApiEvent::getId).orElse(null);
    }

    private boolean hasActivePromotionControl(Long cabinetId, Long advertId, String dedupPrefix) {
        if (cabinetId == null || advertId == null) {
            return false;
        }
        return writer.existsActive(dedupPrefix + ":" + cabinetId + ":" + advertId);
    }

    private void enqueuePromotionAdvertsBatchEvent(
            Cabinet cabinet,
            List<Long> campaignIds,
            int batchIndex,
            WbMainStepPayload payload,
            String triggerSource
    ) {
        String dedupKey = promotionAdvertsDedupPrefix(cabinet.getId(), payload.dateFrom(), payload.dateTo()) + batchIndex;
        WbPromotionAdvertsBatchPayload batchPayload = WbPromotionAdvertsBatchPayload.builder()
                .campaignIds(campaignIds)
                .batchIndex(batchIndex)
                .dateFrom(payload.dateFrom())
                .dateTo(payload.dateTo())
                .includeStocks(payload.includeStocks())
                .build();
        writer.insertIfAbsent(
                cabinet,
                WbApiEventType.PROMOTION_ADVERTS_BATCH,
                WbApiEventExecutors.PROMOTION_ADVERTS_BATCH,
                batchPayload,
                dedupKey,
                MAX_ATTEMPTS,
                PRIORITY,
                triggerSource,
                null
        );
    }

    private void enqueuePromotionStatsBatchEvent(
            Cabinet cabinet,
            List<Long> campaignIds,
            int batchIndex,
            WbMainStepPayload payload,
            String triggerSource,
            int priority
    ) {
        String dedupKey = promotionStatsDedupPrefix(cabinet.getId(), payload.dateFrom(), payload.dateTo()) + batchIndex;
        WbPromotionStatsBatchPayload batchPayload = WbPromotionStatsBatchPayload.builder()
                .campaignIds(campaignIds)
                .batchIndex(batchIndex)
                .dateFrom(payload.dateFrom())
                .dateTo(payload.dateTo())
                .includeStocks(payload.includeStocks())
                .build();
        writer.insertIfAbsent(
                cabinet,
                WbApiEventType.PROMOTION_STATS_BATCH,
                WbApiEventExecutors.PROMOTION_STATS_BATCH,
                batchPayload,
                dedupKey,
                MAX_ATTEMPTS,
                priority,
                triggerSource,
                null
        );
    }

    private void enqueuePromotionNormQueryStatsBatchEvent(
            Cabinet cabinet,
            List<Long> campaignIds,
            int batchIndex,
            WbMainStepPayload payload,
            String triggerSource,
            int priority
    ) {
        String dedupKey = promotionNormQueryStatsDedupPrefix(cabinet.getId(), payload.dateFrom(), payload.dateTo())
                + batchIndex;
        WbPromotionNormQueryStatsBatchPayload batchPayload = WbPromotionNormQueryStatsBatchPayload.builder()
                .campaignIds(campaignIds)
                .batchIndex(batchIndex)
                .dateFrom(payload.dateFrom())
                .dateTo(payload.dateTo())
                .includeStocks(payload.includeStocks())
                .build();
        writer.insertIfAbsent(
                cabinet,
                WbApiEventType.PROMOTION_NORMQUERY_STATS_BATCH,
                WbApiEventExecutors.PROMOTION_NORMQUERY_STATS_BATCH,
                batchPayload,
                dedupKey,
                MAX_ATTEMPTS,
                priority,
                triggerSource,
                null
        );
    }

    /**
     * Приоритет батча статистики: активные выше паузы, пауза выше остальных.
     *
     * @param rank 0 — активна, 1 — пауза, 2 — остальные
     * @return приоритет события очереди
     */
    private static int statsBatchPriority(int rank) {
        if (rank == 0) {
            return STATS_PRIORITY_ACTIVE;
        }
        if (rank == 1) {
            return STATS_PRIORITY_PAUSED;
        }
        return STATS_PRIORITY_REST;
    }

    /**
     * Нарезает список ID на батчи заданного размера без выхода за границы списка.
     *
     * @param ids исходные идентификаторы одной группы статуса
     * @param size максимальный размер HTTP-батча
     * @return подсписки в исходном порядке
     */
    private static List<List<Long>> chunk(List<Long> ids, int size) {
        List<List<Long>> batches = new ArrayList<>();
        if (ids == null || ids.isEmpty() || size <= 0) {
            return batches;
        }
        for (int i = 0; i < ids.size(); i += size) {
            int end = Math.min(i + size, ids.size());
            batches.add(ids.subList(i, end));
        }
        return batches;
    }

    private static String promotionPeriodKey(Long cabinetId, LocalDate from, LocalDate to) {
        return cabinetId + ":" + from + ":" + to;
    }

    private static String promotionAdvertsDedupPrefix(Long cabinetId, LocalDate from, LocalDate to) {
        return "PROMOTION_ADVERTS_BATCH:" + promotionPeriodKey(cabinetId, from, to) + ":";
    }

    private static String promotionStatsDedupPrefix(Long cabinetId, LocalDate from, LocalDate to) {
        return "PROMOTION_STATS_BATCH:" + promotionPeriodKey(cabinetId, from, to) + ":";
    }

    private static String promotionNormQueryStatsDedupPrefix(Long cabinetId, LocalDate from, LocalDate to) {
        return "PROMOTION_NORMQUERY_STATS_BATCH:" + promotionPeriodKey(cabinetId, from, to) + ":";
    }
}
