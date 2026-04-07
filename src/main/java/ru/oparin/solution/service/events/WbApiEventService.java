package ru.oparin.solution.service.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.PageResponse;
import ru.oparin.solution.dto.WbApiEventDto;
import ru.oparin.solution.dto.WbApiEventStatsDto;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.CabinetRepository;
import ru.oparin.solution.repository.WbApiEventRepository;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.ProductCardService;
import ru.oparin.solution.service.events.payload.*;
import ru.oparin.solution.service.sync.PromotionCampaignSyncService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class WbApiEventService {

    public static final String CONTENT_EXECUTOR_BEAN = "contentCardsListPageEventExecutor";
    public static final String ANALYTICS_EXECUTOR_BEAN = "analyticsSalesFunnelEventExecutor";
    public static final String PRICES_BATCH_EXECUTOR_BEAN = "pricesProductsBatchEventExecutor";
    public static final String PRICES_SPP_EXECUTOR_BEAN = "pricesSppOrdersEventExecutor";
    public static final String PROMOTION_COUNT_EXECUTOR_BEAN = "promotionCountEventExecutor";
    public static final String PROMOTION_ADVERTS_BATCH_EXECUTOR_BEAN = "promotionAdvertsBatchEventExecutor";
    public static final String PROMOTION_STATS_BATCH_EXECUTOR_BEAN = "promotionStatsBatchEventExecutor";
    public static final String FEEDBACKS_SYNC_EXECUTOR_BEAN = "feedbacksSyncCabinetEventExecutor";
    public static final String PROMOTION_CALENDAR_SYNC_EXECUTOR_BEAN = "promotionCalendarSyncCabinetEventExecutor";
    public static final String WAREHOUSES_SYNC_EXECUTOR_BEAN = "warehousesSyncCabinetEventExecutor";
    public static final String STOCKS_EXECUTOR_BEAN = "stocksByNmIdEventExecutor";
    private static final int CONTENT_EVENT_MAX_ATTEMPTS = 3;
    private static final int CONTENT_EVENT_PRIORITY = 100;
    private static final int STOCKS_EVENT_MAX_ATTEMPTS = 5;
    private static final int STOCKS_EVENT_PRIORITY = 80;
    private static final int ANALYTICS_EVENT_MAX_ATTEMPTS = 5;
    private static final int ANALYTICS_EVENT_PRIORITY = 90;
    private static final int PRIORITY_CARD_EVENT_BOOST = 1000;
    private static final int PRICES_EVENT_MAX_ATTEMPTS = 5;
    private static final int PRICES_EVENT_PRIORITY = 85;
    private static final int PRICES_BATCH_SIZE = 1000;
    private static final int PROMOTION_EVENT_MAX_ATTEMPTS = 5;
    private static final int PROMOTION_EVENT_PRIORITY = 85;
    private static final int SIDECAR_EVENT_MAX_ATTEMPTS = 5;
    private static final int SIDECAR_EVENT_PRIORITY = 84;
    private static final int WAREHOUSES_EVENT_MAX_ATTEMPTS = 5;
    private static final int WAREHOUSES_EVENT_PRIORITY = 75;

    private static final Set<WbApiEventStatus> ACTIVE_STATUSES = Set.of(
            WbApiEventStatus.CREATED,
            WbApiEventStatus.RUNNING,
            WbApiEventStatus.FAILED_RETRYABLE,
            WbApiEventStatus.DEFERRED_RATE_LIMIT
    );
    private static final List<WbApiEventStatus> RUNNABLE_STATUSES = List.of(
            WbApiEventStatus.CREATED,
            WbApiEventStatus.FAILED_RETRYABLE,
            WbApiEventStatus.DEFERRED_RATE_LIMIT
    );
    private static final List<WbApiEventType> MAIN_EVENT_TYPES = List.of(
            WbApiEventType.ANALYTICS_SALES_FUNNEL_NMID,
            WbApiEventType.PRICES_PRODUCTS_BATCH,
            WbApiEventType.PRICES_SPP_ORDERS,
            WbApiEventType.PROMOTION_COUNT,
            WbApiEventType.PROMOTION_ADVERTS_BATCH,
            WbApiEventType.PROMOTION_STATS_BATCH,
            WbApiEventType.FEEDBACKS_SYNC_CABINET,
            WbApiEventType.PROMOTION_CALENDAR_SYNC_CABINET
    );

    private final WbApiEventRepository eventRepository;
    private final CabinetRepository cabinetRepository;
    private final CabinetService cabinetService;
    private final ProductCardService productCardService;
    private final PromotionCampaignSyncService promotionCampaignSyncService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void enqueueInitialContentEvent(Long cabinetId, LocalDate dateFrom, LocalDate dateTo, boolean includeStocks, String triggerSource) {
        ContentCardsListPagePayload payload = ContentCardsListPagePayload.builder()
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .includeStocks(includeStocks)
                .build();
        String dedupKey = buildContentDedupKey(cabinetId, null, null, dateFrom, dateTo);
        enqueueContentEvent(cabinetId, payload, dedupKey, triggerSource);
    }

    @Transactional
    public void enqueueNextContentEvent(Long cabinetId, ContentCardsListPagePayload payload, String triggerSource) {
        String dedupKey = buildContentDedupKey(
                cabinetId,
                payload.cursorNmId(),
                payload.cursorUpdatedAt(),
                payload.dateFrom(),
                payload.dateTo()
        );
        enqueueContentEvent(cabinetId, payload, dedupKey, triggerSource);
    }

    @Transactional
    public void enqueueStocksByNmIdEvent(Long cabinetId, Long nmId, String triggerSource) {
        String dedupKey = "STOCKS_BY_NMID:" + cabinetId + ":" + nmId;
        if (eventRepository.existsByDedupKeyAndStatusIn(dedupKey, ACTIVE_STATUSES)) {
            log.debug("WB API stocks event уже существует (dedupKey={}), создание пропущено", dedupKey);
            return;
        }
        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден: " + cabinetId));
        StocksByNmIdPayload payload = StocksByNmIdPayload.builder().nmId(nmId).build();
        WbApiEvent event = WbApiEvent.builder()
                .eventType(WbApiEventType.STOCKS_BY_NMID)
                .status(WbApiEventStatus.CREATED)
                .executorBeanName(STOCKS_EXECUTOR_BEAN)
                .cabinet(cabinet)
                .payloadJson(writePayload(payload))
                .dedupKey(dedupKey)
                .attemptCount(0)
                .maxAttempts(STOCKS_EVENT_MAX_ATTEMPTS)
                .nextAttemptAt(LocalDateTime.now())
                .priority(resolveNmIdEventPriority(cabinetId, nmId, STOCKS_EVENT_PRIORITY))
                .triggerSource(triggerSource)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        eventRepository.save(event);
    }

    @Transactional
    public void enqueueAllStocksByNmIdForCabinet(Long cabinetId, String triggerSource) {
        List<Long> nmIds = productCardService.findByCabinetId(cabinetId).stream()
                .map(ProductCard::getNmId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        for (Long nmId : nmIds) {
            enqueueStocksByNmIdEvent(cabinetId, nmId, triggerSource);
        }
    }

    @Transactional
    public void enqueueFeedbacksSyncCabinetEvent(Long cabinetId, MainStepPayload payload, String triggerSource) {
        String dedupKey = "FEEDBACKS_SYNC_CABINET:" + cabinetId + ":" + payload.dateFrom() + ":" + payload.dateTo();
        if (eventRepository.existsByDedupKeyAndStatusIn(dedupKey, ACTIVE_STATUSES)) {
            log.debug("WB API feedbacks sync уже существует (dedupKey={}), создание пропущено", dedupKey);
            return;
        }
        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден: " + cabinetId));
        WbApiEvent event = WbApiEvent.builder()
                .eventType(WbApiEventType.FEEDBACKS_SYNC_CABINET)
                .status(WbApiEventStatus.CREATED)
                .executorBeanName(FEEDBACKS_SYNC_EXECUTOR_BEAN)
                .cabinet(cabinet)
                .payloadJson(writePayload(payload))
                .dedupKey(dedupKey)
                .attemptCount(0)
                .maxAttempts(SIDECAR_EVENT_MAX_ATTEMPTS)
                .nextAttemptAt(LocalDateTime.now())
                .priority(SIDECAR_EVENT_PRIORITY)
                .triggerSource(triggerSource)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        eventRepository.save(event);
    }

    @Transactional
    public void enqueuePromotionCalendarSyncCabinetEvent(Long cabinetId, MainStepPayload payload, String triggerSource) {
        String dedupKey = "PROMOTION_CALENDAR_SYNC_CABINET:" + cabinetId + ":" + payload.dateFrom() + ":" + payload.dateTo();
        if (eventRepository.existsByDedupKeyAndStatusIn(dedupKey, ACTIVE_STATUSES)) {
            log.debug("WB API promotion calendar sync уже существует (dedupKey={}), создание пропущено", dedupKey);
            return;
        }
        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден: " + cabinetId));
        WbApiEvent event = WbApiEvent.builder()
                .eventType(WbApiEventType.PROMOTION_CALENDAR_SYNC_CABINET)
                .status(WbApiEventStatus.CREATED)
                .executorBeanName(PROMOTION_CALENDAR_SYNC_EXECUTOR_BEAN)
                .cabinet(cabinet)
                .payloadJson(writePayload(payload))
                .dedupKey(dedupKey)
                .attemptCount(0)
                .maxAttempts(SIDECAR_EVENT_MAX_ATTEMPTS)
                .nextAttemptAt(LocalDateTime.now())
                .priority(SIDECAR_EVENT_PRIORITY)
                .triggerSource(triggerSource)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        eventRepository.save(event);
    }

    @Transactional
    public void enqueueWarehousesSyncCabinetEvent(Long cabinetId, String triggerSource) {
        String dedupKey = "WAREHOUSES_SYNC_CABINET:" + cabinetId;
        if (eventRepository.existsByDedupKeyAndStatusIn(dedupKey, ACTIVE_STATUSES)) {
            log.debug("WB API warehouses sync уже существует (dedupKey={}), создание пропущено", dedupKey);
            return;
        }
        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден: " + cabinetId));
        LocalDate d = LocalDate.now();
        MainStepPayload payload = MainStepPayload.builder()
                .dateFrom(d)
                .dateTo(d)
                .includeStocks(false)
                .build();
        WbApiEvent event = WbApiEvent.builder()
                .eventType(WbApiEventType.WAREHOUSES_SYNC_CABINET)
                .status(WbApiEventStatus.CREATED)
                .executorBeanName(WAREHOUSES_SYNC_EXECUTOR_BEAN)
                .cabinet(cabinet)
                .payloadJson(writePayload(payload))
                .dedupKey(dedupKey)
                .attemptCount(0)
                .maxAttempts(WAREHOUSES_EVENT_MAX_ATTEMPTS)
                .nextAttemptAt(LocalDateTime.now())
                .priority(WAREHOUSES_EVENT_PRIORITY)
                .triggerSource(triggerSource)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        eventRepository.save(event);
    }

    @Transactional
    public void enqueueAnalyticsSalesFunnelEvent(
            Long cabinetId,
            Long nmId,
            LocalDate dateFrom,
            LocalDate dateTo,
            boolean includeStocks,
            String triggerSource
    ) {
        String dedupKey = "ANALYTICS_SALES_FUNNEL_NMID:" + cabinetId + ":" + nmId + ":" + dateFrom + ":" + dateTo;
        if (eventRepository.existsByDedupKeyAndStatusIn(dedupKey, ACTIVE_STATUSES)) {
            log.debug("WB API analytics event уже существует (dedupKey={}), создание пропущено", dedupKey);
            return;
        }
        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден: " + cabinetId));
        AnalyticsSalesFunnelPayload payload = AnalyticsSalesFunnelPayload.builder()
                .nmId(nmId)
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .includeStocks(includeStocks)
                .build();
        WbApiEvent event = WbApiEvent.builder()
                .eventType(WbApiEventType.ANALYTICS_SALES_FUNNEL_NMID)
                .status(WbApiEventStatus.CREATED)
                .executorBeanName(ANALYTICS_EXECUTOR_BEAN)
                .cabinet(cabinet)
                .payloadJson(writePayload(payload))
                .dedupKey(dedupKey)
                .attemptCount(0)
                .maxAttempts(ANALYTICS_EVENT_MAX_ATTEMPTS)
                .nextAttemptAt(LocalDateTime.now())
                .priority(resolveNmIdEventPriority(cabinetId, nmId, ANALYTICS_EVENT_PRIORITY))
                .triggerSource(triggerSource)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        eventRepository.save(event);
    }

    @Transactional
    public void enqueuePricesRequestLevelEvents(Long cabinetId, MainStepPayload payload, String triggerSource) {
        List<Long> nmIds = productCardService.findByCabinetId(cabinetId).stream()
                .map(ProductCard::getNmId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        for (int i = 0; i < nmIds.size(); i += PRICES_BATCH_SIZE) {
            int end = Math.min(i + PRICES_BATCH_SIZE, nmIds.size());
            List<Long> batch = nmIds.subList(i, end);
            enqueuePricesBatchEvent(cabinetId, batch, payload, triggerSource);
        }
        enqueuePricesSppEvent(cabinetId, payload, triggerSource);
    }

    /**
     * @return {@code true}, если создано новое событие PROMOTION_COUNT; {@code false}, если активная задача с тем же периодом уже есть
     */
    @Transactional
    public boolean enqueuePromotionRequestLevelEvents(Long cabinetId, MainStepPayload payload, String triggerSource) {
        String dedupKey = "PROMOTION_COUNT:" + promotionPeriodKey(cabinetId, payload.dateFrom(), payload.dateTo());
        if (eventRepository.existsByDedupKeyAndStatusIn(dedupKey, ACTIVE_STATUSES)) {
            log.debug("WB API promotion count event уже существует (dedupKey={}), создание пропущено", dedupKey);
            return false;
        }
        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден: " + cabinetId));
        WbApiEvent event = WbApiEvent.builder()
                .eventType(WbApiEventType.PROMOTION_COUNT)
                .status(WbApiEventStatus.CREATED)
                .executorBeanName(PROMOTION_COUNT_EXECUTOR_BEAN)
                .cabinet(cabinet)
                .payloadJson(writePayload(payload))
                .dedupKey(dedupKey)
                .attemptCount(0)
                .maxAttempts(PROMOTION_EVENT_MAX_ATTEMPTS)
                .nextAttemptAt(LocalDateTime.now())
                .priority(PROMOTION_EVENT_PRIORITY)
                .triggerSource(triggerSource)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        eventRepository.save(event);
        return true;
    }

    @Transactional
    public void enqueuePromotionAdvertsBatchEvents(Long cabinetId, MainStepPayload payload, List<Long> campaignIds, String triggerSource) {
        int size = promotionCampaignSyncService.getCampaignsBatchSize();
        for (int i = 0, batchIndex = 0; i < campaignIds.size(); i += size, batchIndex++) {
            int end = Math.min(i + size, campaignIds.size());
            List<Long> batch = campaignIds.subList(i, end);
            enqueuePromotionAdvertsBatchEvent(cabinetId, batch, batchIndex, payload, triggerSource);
        }
    }

    @Transactional
    public void schedulePromotionStatsAfterAdvertsIfReady(
            Long cabinetId,
            MainStepPayload payload,
            String triggerSource,
            long excludeAdvertBatchEventId
    ) {
        String advertsPrefix = promotionAdvertsDedupPrefix(cabinetId, payload.dateFrom(), payload.dateTo());
        if (eventRepository.existsOtherByCabinet_IdAndEventTypeAndStatusInAndDedupKeyPrefix(
                cabinetId,
                WbApiEventType.PROMOTION_ADVERTS_BATCH,
                ACTIVE_STATUSES,
                advertsPrefix,
                excludeAdvertBatchEventId
        )) {
            return;
        }
        List<Long> needing = promotionCampaignSyncService.listCampaignIdsNeedingStatisticsForPeriod(
                cabinetId,
                payload.dateFrom(),
                payload.dateTo()
        );
        if (needing.isEmpty()) {
            tryFinalizeMain(cabinetId, excludeAdvertBatchEventId);
            return;
        }
        String statsPrefix = promotionStatsDedupPrefix(cabinetId, payload.dateFrom(), payload.dateTo());
        if (eventRepository.existsByCabinet_IdAndEventTypeAndStatusInAndDedupKeyPrefix(
                cabinetId,
                WbApiEventType.PROMOTION_STATS_BATCH,
                ACTIVE_STATUSES,
                statsPrefix
        )) {
            return;
        }
        int statBatchSize = promotionCampaignSyncService.getStatisticsBatchSize();
        for (int i = 0, batchIndex = 0; i < needing.size(); i += statBatchSize, batchIndex++) {
            int end = Math.min(i + statBatchSize, needing.size());
            List<Long> batch = needing.subList(i, end);
            enqueuePromotionStatsBatchEvent(cabinetId, batch, batchIndex, payload, triggerSource);
        }
    }

    @Transactional(readOnly = true)
    public boolean hasOtherActivePromotionStatsBatches(Long cabinetId, Long excludeEventId, LocalDate dateFrom, LocalDate dateTo) {
        return eventRepository.existsOtherByCabinet_IdAndEventTypeAndStatusInAndDedupKeyPrefix(
                cabinetId,
                WbApiEventType.PROMOTION_STATS_BATCH,
                ACTIVE_STATUSES,
                promotionStatsDedupPrefix(cabinetId, dateFrom, dateTo),
                excludeEventId
        );
    }

    private void enqueuePromotionAdvertsBatchEvent(
            Long cabinetId,
            List<Long> campaignIds,
            int batchIndex,
            MainStepPayload payload,
            String triggerSource
    ) {
        String dedupKey = promotionAdvertsDedupPrefix(cabinetId, payload.dateFrom(), payload.dateTo()) + batchIndex;
        if (eventRepository.existsByDedupKeyAndStatusIn(dedupKey, ACTIVE_STATUSES)) {
            log.debug("WB API promotion adverts batch уже существует (dedupKey={}), создание пропущено", dedupKey);
            return;
        }
        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден: " + cabinetId));
        PromotionAdvertsBatchPayload batchPayload = PromotionAdvertsBatchPayload.builder()
                .campaignIds(campaignIds)
                .batchIndex(batchIndex)
                .dateFrom(payload.dateFrom())
                .dateTo(payload.dateTo())
                .includeStocks(payload.includeStocks())
                .build();
        WbApiEvent event = WbApiEvent.builder()
                .eventType(WbApiEventType.PROMOTION_ADVERTS_BATCH)
                .status(WbApiEventStatus.CREATED)
                .executorBeanName(PROMOTION_ADVERTS_BATCH_EXECUTOR_BEAN)
                .cabinet(cabinet)
                .payloadJson(writePayload(batchPayload))
                .dedupKey(dedupKey)
                .attemptCount(0)
                .maxAttempts(PROMOTION_EVENT_MAX_ATTEMPTS)
                .nextAttemptAt(LocalDateTime.now())
                .priority(PROMOTION_EVENT_PRIORITY)
                .triggerSource(triggerSource)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        eventRepository.save(event);
    }

    private void enqueuePromotionStatsBatchEvent(
            Long cabinetId,
            List<Long> campaignIds,
            int batchIndex,
            MainStepPayload payload,
            String triggerSource
    ) {
        String dedupKey = promotionStatsDedupPrefix(cabinetId, payload.dateFrom(), payload.dateTo()) + batchIndex;
        if (eventRepository.existsByDedupKeyAndStatusIn(dedupKey, ACTIVE_STATUSES)) {
            log.debug("WB API promotion stats batch уже существует (dedupKey={}), создание пропущено", dedupKey);
            return;
        }
        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден: " + cabinetId));
        PromotionStatsBatchPayload batchPayload = PromotionStatsBatchPayload.builder()
                .campaignIds(campaignIds)
                .batchIndex(batchIndex)
                .dateFrom(payload.dateFrom())
                .dateTo(payload.dateTo())
                .includeStocks(payload.includeStocks())
                .build();
        WbApiEvent event = WbApiEvent.builder()
                .eventType(WbApiEventType.PROMOTION_STATS_BATCH)
                .status(WbApiEventStatus.CREATED)
                .executorBeanName(PROMOTION_STATS_BATCH_EXECUTOR_BEAN)
                .cabinet(cabinet)
                .payloadJson(writePayload(batchPayload))
                .dedupKey(dedupKey)
                .attemptCount(0)
                .maxAttempts(PROMOTION_EVENT_MAX_ATTEMPTS)
                .nextAttemptAt(LocalDateTime.now())
                .priority(PROMOTION_EVENT_PRIORITY)
                .triggerSource(triggerSource)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        eventRepository.save(event);
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

    @Transactional
    public void enqueuePricesBatchEvent(Long cabinetId, List<Long> nmIds, MainStepPayload payload, String triggerSource) {
        String dedupKey = "PRICES_PRODUCTS_BATCH:" + cabinetId + ":" + payload.dateFrom() + ":" + payload.dateTo() + ":" + Integer.toHexString(nmIds.hashCode());
        if (eventRepository.existsByDedupKeyAndStatusIn(dedupKey, ACTIVE_STATUSES)) {
            log.debug("WB API prices batch event уже существует (dedupKey={}), создание пропущено", dedupKey);
            return;
        }
        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден: " + cabinetId));
        PricesProductsBatchPayload batchPayload = PricesProductsBatchPayload.builder()
                .nmIds(nmIds)
                .dateFrom(payload.dateFrom())
                .dateTo(payload.dateTo())
                .includeStocks(payload.includeStocks())
                .build();
        WbApiEvent event = WbApiEvent.builder()
                .eventType(WbApiEventType.PRICES_PRODUCTS_BATCH)
                .status(WbApiEventStatus.CREATED)
                .executorBeanName(PRICES_BATCH_EXECUTOR_BEAN)
                .cabinet(cabinet)
                .payloadJson(writePayload(batchPayload))
                .dedupKey(dedupKey)
                .attemptCount(0)
                .maxAttempts(PRICES_EVENT_MAX_ATTEMPTS)
                .nextAttemptAt(LocalDateTime.now())
                .priority(PRICES_EVENT_PRIORITY)
                .triggerSource(triggerSource)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        eventRepository.save(event);
    }

    @Transactional
    public void enqueuePricesSppEvent(Long cabinetId, MainStepPayload payload, String triggerSource) {
        String dedupKey = "PRICES_SPP_ORDERS:" + cabinetId + ":" + payload.dateFrom() + ":" + payload.dateTo();
        if (eventRepository.existsByDedupKeyAndStatusIn(dedupKey, ACTIVE_STATUSES)) {
            log.debug("WB API SPP event уже существует (dedupKey={}), создание пропущено", dedupKey);
            return;
        }
        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден: " + cabinetId));
        WbApiEvent event = WbApiEvent.builder()
                .eventType(WbApiEventType.PRICES_SPP_ORDERS)
                .status(WbApiEventStatus.CREATED)
                .executorBeanName(PRICES_SPP_EXECUTOR_BEAN)
                .cabinet(cabinet)
                .payloadJson(writePayload(payload))
                .dedupKey(dedupKey)
                .attemptCount(0)
                .maxAttempts(PRICES_EVENT_MAX_ATTEMPTS)
                .nextAttemptAt(LocalDateTime.now())
                .priority(PRICES_EVENT_PRIORITY)
                .triggerSource(triggerSource)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        eventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public List<WbApiEvent> findDueEvents() {
        return eventRepository.findReadyEvents(
                List.of(
                        WbApiEventStatus.CREATED,
                        WbApiEventStatus.FAILED_RETRYABLE,
                        WbApiEventStatus.DEFERRED_RATE_LIMIT
                ),
                LocalDateTime.now()
        );
    }

    @Transactional
    public boolean tryMarkRunning(WbApiEvent event) {
        int updated = eventRepository.tryMarkRunning(
                event.getId(),
                event.getCabinet().getId(),
                event.getEventType(),
                RUNNABLE_STATUSES,
                WbApiEventStatus.RUNNING,
                LocalDateTime.now()
        );
        return updated > 0;
    }

    @Transactional
    public int recoverStuckRunningEvents(int timeoutMinutes) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<WbApiEvent> stuck = eventRepository.findByStatusAndStartedAtBefore(WbApiEventStatus.RUNNING, threshold);
        for (WbApiEvent event : stuck) {
            event.setStatus(WbApiEventStatus.FAILED_RETRYABLE);
            event.setLastError("Автовосстановление: событие было RUNNING дольше " + timeoutMinutes + " мин");
            event.setNextAttemptAt(LocalDateTime.now().plusSeconds(10));
            event.setUpdatedAt(LocalDateTime.now());
        }
        eventRepository.saveAll(stuck);
        return stuck.size();
    }

    /**
     * Фиксирует успешное завершение основной волны по кабинету. События остатков при includeStocks
     * создаются раньше — после полной загрузки карточек в {@link ContentCardsListPageEventExecutor}.
     */
    @Transactional
    public void markMainCompleted(Long cabinetId) {
        Cabinet cabinet = cabinetService.findByIdWithUserOrThrow(cabinetId);
        cabinet.setLastDataUpdateAt(LocalDateTime.now());
        cabinetService.save(cabinet);
    }

    /**
     * @param excludeEventId событие, которое сейчас выполняется (RUNNING) — не учитывать при проверке «есть ли ещё main-work».
     */
    @Transactional
    public void tryFinalizeMain(Long cabinetId, Long excludeEventId) {
        boolean hasPendingMain = eventRepository.existsByCabinet_IdAndEventTypeInAndStatusInExcludingEventId(
                cabinetId,
                MAIN_EVENT_TYPES,
                ACTIVE_STATUSES,
                excludeEventId
        );
        if (!hasPendingMain) {
            markMainCompleted(cabinetId);
        }
    }

    @Transactional
    public long deleteOldSuccessfulEvents(int hours) {
        LocalDateTime threshold = LocalDateTime.now().minusHours(hours);
        return eventRepository.deleteByStatusAndFinishedAtBefore(WbApiEventStatus.SUCCESS, threshold);
    }

    @Transactional(readOnly = true)
    public WbApiEventStatsDto getStats() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (WbApiEventStatus status : WbApiEventStatus.values()) {
            byStatus.put(status.name(), eventRepository.countByStatus(status));
        }
        return WbApiEventStatsDto.builder()
                .total(eventRepository.count())
                .byStatus(byStatus)
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<WbApiEventDto> getEventsPage(
            int page,
            int size,
            WbApiEventStatus status,
            WbApiEventType eventType,
            Long cabinetId
    ) {
        Pageable pageable = PageRequest.of(page, Math.clamp(size, 1, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<WbApiEvent> eventsPage = eventRepository.findAdminEvents(status, eventType, cabinetId, pageable);
        List<WbApiEventDto> content = eventsPage.getContent().stream().map(this::toDto).toList();
        return PageResponse.<WbApiEventDto>builder()
                .content(content)
                .totalElements(eventsPage.getTotalElements())
                .totalPages(eventsPage.getTotalPages())
                .size(eventsPage.getSize())
                .number(eventsPage.getNumber())
                .build();
    }

    @Transactional(readOnly = true)
    public WbApiEventDto getEventById(Long eventId) {
        WbApiEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Событие не найдено: " + eventId));
        return toDto(event);
    }

    @Transactional
    public void retryNow(Long eventId) {
        WbApiEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Событие не найдено: " + eventId));
        event.setStatus(WbApiEventStatus.CREATED);
        event.setNextAttemptAt(LocalDateTime.now());
        event.setLastError(null);
        event.setFinishedAt(null);
        event.setUpdatedAt(LocalDateTime.now());
        eventRepository.save(event);
    }

    @Transactional
    public void cancel(Long eventId) {
        WbApiEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Событие не найдено: " + eventId));
        event.setStatus(WbApiEventStatus.CANCELLED);
        event.setFinishedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        eventRepository.save(event);
    }

    @Transactional
    public void markSuccess(WbApiEvent event) {
        event.setStatus(WbApiEventStatus.SUCCESS);
        event.setFinishedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        eventRepository.save(event);
    }

    @Transactional
    public void markFailed(WbApiEvent event, WbApiEventExecutionResult result) {
        event.setLastError(result.errorMessage());
        event.setUpdatedAt(LocalDateTime.now());
        Long cabinetId = event.getCabinet() != null ? event.getCabinet().getId() : null;

        if (result.deferUntil() != null) {
            event.setStatus(WbApiEventStatus.DEFERRED_RATE_LIMIT);
            event.setNextAttemptAt(result.deferUntil());
            eventRepository.save(event);
            return;
        }

        int nextAttempt = event.getAttemptCount() + 1;
        event.setAttemptCount(nextAttempt);

        if (result.retryable() && nextAttempt < event.getMaxAttempts()) {
            event.setStatus(WbApiEventStatus.FAILED_RETRYABLE);
            event.setNextAttemptAt(LocalDateTime.now().plusSeconds(20L * nextAttempt));
            eventRepository.save(event);
            return;
        }

        event.setStatus(result.fallbackUsed() ? WbApiEventStatus.FAILED_WITH_FALLBACK : WbApiEventStatus.FAILED_FINAL);
        event.setFinishedAt(LocalDateTime.now());
        eventRepository.save(event);
        log.warn(
                "WB event завершен с ошибкой: id={}, type={}, cabinetId={}, status={}, attempts={}/{}, error={}",
                event.getId(),
                event.getEventType(),
                cabinetId,
                event.getStatus(),
                event.getAttemptCount(),
                event.getMaxAttempts(),
                event.getLastError()
        );
    }

    public <T> T readPayload(WbApiEvent event, Class<T> payloadType) {
        try {
            return objectMapper.readValue(event.getPayloadJson(), payloadType);
        } catch (Exception e) {
            throw new IllegalArgumentException("Некорректный payload события " + event.getId() + ": " + e.getMessage(), e);
        }
    }

    private void enqueueContentEvent(Long cabinetId, ContentCardsListPagePayload payload, String dedupKey, String triggerSource) {
        if (eventRepository.existsByDedupKeyAndStatusIn(dedupKey, ACTIVE_STATUSES)) {
            log.debug("WB API event уже существует (dedupKey={}), создание пропущено", dedupKey);
            return;
        }
        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден: " + cabinetId));
        WbApiEvent event = WbApiEvent.builder()
                .eventType(WbApiEventType.CONTENT_CARDS_LIST_PAGE)
                .status(WbApiEventStatus.CREATED)
                .executorBeanName(CONTENT_EXECUTOR_BEAN)
                .cabinet(cabinet)
                .payloadJson(writePayload(payload))
                .dedupKey(dedupKey)
                .attemptCount(0)
                .maxAttempts(CONTENT_EVENT_MAX_ATTEMPTS)
                .nextAttemptAt(LocalDateTime.now())
                .priority(CONTENT_EVENT_PRIORITY)
                .triggerSource(triggerSource)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        eventRepository.save(event);
    }

    private String writePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Не удалось сериализовать payload события: " + e.getMessage(), e);
        }
    }

    private int resolveNmIdEventPriority(Long cabinetId, Long nmId, int basePriority) {
        if (cabinetId == null || nmId == null) {
            return basePriority;
        }
        return productCardService.findByNmIdAndCabinetId(nmId, cabinetId)
                .map(card -> Boolean.TRUE.equals(card.getIsPriority()) ? basePriority + PRIORITY_CARD_EVENT_BOOST : basePriority)
                .orElse(basePriority);
    }

    private String buildContentDedupKey(Long cabinetId, Long cursorNmId, String cursorUpdatedAt, LocalDate from, LocalDate to) {
        return "CONTENT_CARDS_LIST_PAGE:"
                + cabinetId + ":"
                + (cursorNmId == null ? "first" : cursorNmId) + ":"
                + (cursorUpdatedAt == null ? "null" : cursorUpdatedAt) + ":"
                + from + ":" + to;
    }

    private WbApiEventDto toDto(WbApiEvent event) {
        return WbApiEventDto.builder()
                .id(event.getId())
                .eventType(event.getEventType().name())
                .status(event.getStatus().name())
                .executorBeanName(event.getExecutorBeanName())
                .cabinetId(event.getCabinet() != null ? event.getCabinet().getId() : null)
                .dedupKey(event.getDedupKey())
                .attemptCount(event.getAttemptCount())
                .maxAttempts(event.getMaxAttempts())
                .nextAttemptAt(event.getNextAttemptAt())
                .lastError(event.getLastError())
                .priority(event.getPriority())
                .triggerSource(event.getTriggerSource())
                .createdAt(event.getCreatedAt())
                .startedAt(event.getStartedAt())
                .finishedAt(event.getFinishedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }
}
