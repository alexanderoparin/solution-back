package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.*;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.model.WbApiEventStatus;
import ru.oparin.solution.model.WbApiEventType;
import ru.oparin.solution.service.events.enqueue.*;
import ru.oparin.solution.service.events.payload.WbAbTestStartPayload;
import ru.oparin.solution.service.events.payload.WbContentCardsListPagePayload;
import ru.oparin.solution.service.events.payload.WbItemRatingSyncStepPayload;
import ru.oparin.solution.service.events.payload.WbMainStepPayload;

import java.time.LocalDate;
import java.util.List;

/**
 * Фасад очереди WB API: постановка событий, жизненный цикл и админские выборки.
 */
@Service
@RequiredArgsConstructor
public class WbApiEventService {

    private final WbContentEventEnqueue contentEnqueue;
    private final WbStocksEventEnqueue stocksEnqueue;
    private final WbPricesEventEnqueue pricesEnqueue;
    private final WbSidecarEventEnqueue sidecarEnqueue;
    private final WbAnalyticsFunnelEventEnqueue funnelEnqueue;
    private final WbPromotionEventEnqueue promotionEnqueue;
    private final WbAbTestEventEnqueue abTestEnqueue;
    private final WbApiEventQueueService queueService;
    private final WbApiEventAdminQuery adminQuery;
    private final WbApiEventWriter writer;

    /**
     * Первая страница карточек кабинета за период.
     */
    @Transactional
    public void enqueueInitialContentEvent(
            Long cabinetId,
            LocalDate dateFrom,
            LocalDate dateTo,
            boolean includeStocks,
            String triggerSource
    ) {
        contentEnqueue.enqueueInitialContentEvent(cabinetId, dateFrom, dateTo, includeStocks, triggerSource);
    }

    /**
     * Следующая страница карточек по курсору.
     */
    @Transactional
    public void enqueueNextContentEvent(Long cabinetId, WbContentCardsListPagePayload payload, String triggerSource) {
        contentEnqueue.enqueueNextContentEvent(cabinetId, payload, triggerSource);
    }

    /**
     * Остатки всех артикулов кабинета + склады продавца FBS.
     */
    @Transactional
    public void enqueueAllStocksByNmIdForCabinet(Long cabinetId, String triggerSource) {
        stocksEnqueue.enqueueAllStocksByNmIdForCabinet(cabinetId, triggerSource);
    }

    /**
     * Первый шаг item-rating sync (BASIC-токен пропускается).
     */
    @Transactional
    public void enqueueItemRatingSyncCabinetEvent(Long cabinetId, WbMainStepPayload payload, String triggerSource) {
        sidecarEnqueue.enqueueItemRatingSyncCabinetEvent(cabinetId, payload, triggerSource);
    }

    /**
     * Следующий шаг item-rating с задержкой под лимит API.
     */
    @Transactional
    public void enqueueNextItemRatingStepEvent(
            Long cabinetId,
            WbItemRatingSyncStepPayload payload,
            String triggerSource
    ) {
        sidecarEnqueue.enqueueNextItemRatingStepEvent(cabinetId, payload, triggerSource);
    }

    /**
     * Синхронизация календаря акций кабинета.
     */
    @Transactional
    public void enqueuePromotionCalendarSyncCabinetEvent(
            Long cabinetId,
            WbMainStepPayload payload,
            String triggerSource
    ) {
        sidecarEnqueue.enqueuePromotionCalendarSyncCabinetEvent(cabinetId, payload, triggerSource);
    }

    /**
     * Склады WB кабинета; всегда дополнительно ставит FBS warehouses.
     */
    @Transactional
    public void enqueueWarehousesSyncCabinetEvent(Long cabinetId, String triggerSource) {
        stocksEnqueue.enqueueWarehousesSyncCabinetEvent(cabinetId, triggerSource);
    }

    /**
     * Остатки FBS по всем складам продавца кабинета.
     */
    @Transactional
    public void enqueueFbsStocksCabinetEvent(Long cabinetId, String triggerSource) {
        stocksEnqueue.enqueueFbsStocksCabinetEvent(cabinetId, triggerSource);
    }

    /**
     * Воронка продаж одного артикула за период.
     */
    @Transactional
    public void enqueueAnalyticsSalesFunnelEvent(
            Long cabinetId,
            Long nmId,
            LocalDate dateFrom,
            LocalDate dateTo,
            boolean includeStocks,
            String triggerSource
    ) {
        funnelEnqueue.enqueueAnalyticsSalesFunnelEvent(
                cabinetId, nmId, dateFrom, dateTo, includeStocks, triggerSource);
    }

    /**
     * Уровень запроса цен: одно событие prices+СПП.
     */
    @Transactional
    public void enqueuePricesRequestLevelEvents(Long cabinetId, WbMainStepPayload payload, String triggerSource) {
        pricesEnqueue.enqueuePricesRequestLevelEvents(cabinetId, payload, triggerSource);
    }

    /**
     * Поставить в очередь запуск рекламной кампании.
     *
     * @return id созданного события или {@code null}, если задача уже в очереди
     */
    @Transactional
    public Long enqueueWbPromotionCampaignStart(Long cabinetId, Long advertId, String triggerSource) {
        return promotionEnqueue.enqueueWbPromotionCampaignStart(cabinetId, advertId, triggerSource);
    }

    /**
     * Поставить в очередь паузу рекламной кампании.
     *
     * @return id созданного события или {@code null}, если задача уже в очереди
     */
    @Transactional
    public Long enqueueWbPromotionCampaignPause(Long cabinetId, Long advertId, String triggerSource) {
        return promotionEnqueue.enqueueWbPromotionCampaignPause(cabinetId, advertId, triggerSource);
    }

    /**
     * @return {@code true}, если создано новое событие PROMOTION_COUNT
     */
    @Transactional
    public boolean enqueuePromotionRequestLevelEvents(Long cabinetId, WbMainStepPayload payload, String triggerSource) {
        return promotionEnqueue.enqueuePromotionRequestLevelEvents(cabinetId, payload, triggerSource);
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
        promotionEnqueue.enqueuePromotionAdvertsBatchEvents(cabinetId, payload, campaignIds, triggerSource);
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
        promotionEnqueue.schedulePromotionStatsAfterAdvertsIfReady(
                cabinetId, payload, triggerSource, excludeAdvertBatchEventId);
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
        return promotionEnqueue.hasOtherActivePromotionStatsBatches(cabinetId, excludeEventId, dateFrom, dateTo);
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
        promotionEnqueue.schedulePromotionNormQueryStatsIfReady(
                cabinetId, dateFrom, dateTo, includeStocks, triggerSource, excludeStatsBatchEventId);
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
        return promotionEnqueue.hasOtherActivePromotionNormQueryStatsBatches(
                cabinetId, excludeEventId, dateFrom, dateTo);
    }

    /**
     * Активный запуск РК в очереди.
     */
    @Transactional(readOnly = true)
    public boolean hasActiveWbPromotionCampaignStart(Long cabinetId, Long advertId) {
        return promotionEnqueue.hasActiveWbPromotionCampaignStart(cabinetId, advertId);
    }

    /**
     * Активная пауза РК в очереди.
     */
    @Transactional(readOnly = true)
    public boolean hasActiveWbPromotionCampaignPause(Long cabinetId, Long advertId) {
        return promotionEnqueue.hasActiveWbPromotionCampaignPause(cabinetId, advertId);
    }

    /**
     * Due-события для poll: не больше одного на пару (кабинет, тип события).
     */
    @Transactional(readOnly = true)
    public List<WbApiEvent> findDueEvents() {
        return queueService.findDueEvents();
    }

    /**
     * Атомарно переводит событие в RUNNING, если оно ещё runnable.
     */
    @Transactional
    public boolean tryMarkRunning(WbApiEvent event) {
        return queueService.tryMarkRunning(event);
    }

    /**
     * После таймаута выполнения с момента {@code tryMarkRunning} событие могло остаться RUNNING — переводим в retry.
     */
    @Transactional
    public boolean revertRunningAfterExecutionTimeout(long eventId, int executionTimeoutSeconds) {
        return queueService.revertRunningAfterExecutionTimeout(eventId, executionTimeoutSeconds);
    }

    /**
     * Успех только если событие ещё в RUNNING (иначе таймаут выполнения уже перевёл в retry).
     */
    @Transactional
    public void markSuccessIfRunning(Long eventId) {
        queueService.markSuccessIfRunning(eventId);
    }

    /**
     * Ошибка выполнения только если событие ещё в RUNNING.
     */
    @Transactional
    public void markFailedIfRunning(Long eventId, WbApiEventExecutionResult result) {
        queueService.markFailedIfRunning(eventId, result);
    }

    /**
     * RUNNING дольше timeoutMinutes → FAILED_RETRYABLE.
     */
    @Transactional
    public int recoverStuckRunningEvents(int timeoutMinutes) {
        return queueService.recoverStuckRunningEvents(timeoutMinutes);
    }

    /**
     * После остановки JVM события могли остаться в RUNNING. Переводим их в повтор без увеличения счётчика попыток.
     */
    @Transactional
    public int recoverRunningEventsAfterJvmStop() {
        return queueService.recoverRunningEventsAfterJvmStop();
    }

    /**
     * @param excludeEventId событие, которое сейчас выполняется (RUNNING) — не учитывать при проверке «есть ли ещё main-work».
     */
    @Transactional
    public void tryFinalizeMain(Long cabinetId, Long excludeEventId) {
        queueService.tryFinalizeMain(cabinetId, excludeEventId);
    }

    /**
     * Удаляет успешно завершённые события старше {@code hours} часов.
     */
    @Transactional
    public long deleteOldSuccessfulEvents(int hours) {
        return queueService.deleteOldSuccessfulEvents(hours);
    }

    /**
     * Счётчики по статусам.
     */
    @Transactional(readOnly = true)
    public WbApiEventStatsDto getStats() {
        return adminQuery.getStats();
    }

    /**
     * Счётчики по типам событий (опционально в рамках статуса).
     */
    @Transactional(readOnly = true)
    public WbApiEventTypeStatsDto getStatsByType(WbApiEventStatus status) {
        return adminQuery.getStatsByType(status);
    }

    /**
     * Счётчики по кабинетам.
     */
    @Transactional(readOnly = true)
    public WbApiEventCabinetStatsDto getStatsByCabinet(WbApiEventStatus status, WbApiEventType eventType) {
        return adminQuery.getStatsByCabinet(status, eventType);
    }

    /**
     * Страница событий для админки.
     */
    @Transactional(readOnly = true)
    public PageResponse<WbApiEventDto> getEventsPage(
            int page,
            int size,
            WbApiEventStatus status,
            WbApiEventType eventType,
            Long cabinetId,
            WbApiEventSortField sortBy,
            Sort.Direction sortDir
    ) {
        return adminQuery.getEventsPage(page, size, status, eventType, cabinetId, sortBy, sortDir);
    }

    /**
     * Одно событие по id.
     */
    @Transactional(readOnly = true)
    public WbApiEventDto getEventById(Long eventId) {
        return adminQuery.getEventById(eventId);
    }

    /**
     * Сбрасывает событие в CREATED для немедленного повтора.
     */
    @Transactional
    public void retryNow(Long eventId) {
        queueService.retryNow(eventId);
    }

    /**
     * Массовый перевод FAILED_FINAL → CREATED.
     */
    @Transactional
    public int retryAllFailedFinalNow() {
        return queueService.retryAllFailedFinalNow();
    }

    /**
     * Отменяет событие.
     */
    @Transactional
    public void cancel(Long eventId) {
        queueService.cancel(eventId);
    }

    /**
     * Обрабатывает ошибку выполнения: retry, defer rate-limit или финальный статус.
     */
    @Transactional
    public void markFailed(WbApiEvent event, WbApiEventExecutionResult result) {
        queueService.markFailed(event, result);
    }

    /**
     * Первый шаг старта А/Б-теста ({@code RESOLVE_CARD}).
     *
     * @return id события или null, если уже в очереди
     */
    @Transactional
    public Long enqueueWbAbTestStart(Long cabinetId, Long abTestId, String triggerSource) {
        return abTestEnqueue.enqueueWbAbTestStart(cabinetId, abTestId, triggerSource);
    }

    /**
     * Следующий шаг старта А/Б с паузой под лимит media Content API.
     */
    @Transactional
    public Long enqueueNextWbAbTestStartStep(Long cabinetId, WbAbTestStartPayload payload, String triggerSource) {
        return abTestEnqueue.enqueueNextWbAbTestStartStep(cabinetId, payload, triggerSource);
    }

    /**
     * Смена главного фото А/Б-теста.
     *
     * @return id события или null, если уже в очереди
     */
    @Transactional
    public Long enqueueWbAbTestApplyPhoto(
            Long cabinetId,
            Long abTestId,
            Long variantId,
            String reason,
            boolean finishAfterApply,
            String triggerSource
    ) {
        return abTestEnqueue.enqueueWbAbTestApplyPhoto(
                cabinetId, abTestId, variantId, reason, finishAfterApply, triggerSource);
    }

    /**
     * Опрос fullstats для А/Б-теста.
     *
     * @return id события или null, если уже в очереди
     */
    @Transactional
    public Long enqueueWbAbTestStatsPoll(Long cabinetId, Long abTestId, String triggerSource) {
        return abTestEnqueue.enqueueWbAbTestStatsPoll(cabinetId, abTestId, triggerSource);
    }

    /**
     * Читает JSON payload события в тип {@code payloadType}.
     */
    public <T> T readPayload(WbApiEvent event, Class<T> payloadType) {
        return writer.readPayload(event, payloadType);
    }
}
