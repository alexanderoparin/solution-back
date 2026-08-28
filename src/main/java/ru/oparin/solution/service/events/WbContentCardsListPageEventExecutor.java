package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.oparin.solution.dto.wb.WbCardsListRequest;
import ru.oparin.solution.dto.wb.WbCardsListResponse;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.CabinetUpdateErrorScope;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.model.WbProductCard;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.CabinetUpdateErrorService;
import ru.oparin.solution.service.WbProductCardService;
import ru.oparin.solution.service.events.payload.WbContentCardsListPagePayload;
import ru.oparin.solution.service.events.payload.WbMainStepPayload;
import ru.oparin.solution.service.wb.WbContentApiClient;
import ru.oparin.solution.util.WbSyncPeriods;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Component("contentCardsListPageEventExecutor")
@RequiredArgsConstructor
@Slf4j
public class WbContentCardsListPageEventExecutor implements WbApiEventExecutor {

    private static final int CARDS_PAGE_LIMIT = 100;
    private static final int WITH_PHOTO_ALL = -1;

    private final WbApiEventService eventService;
    private final CabinetService cabinetService;
    private final WbContentApiClient contentApiClient;
    private final WbProductCardService productCardService;
    private final CabinetUpdateErrorService cabinetUpdateErrorService;

    @Override
    public WbApiEventExecutionResult execute(WbApiEvent event) {
        WbContentCardsListPagePayload payload = eventService.readPayload(event, WbContentCardsListPagePayload.class);
        Cabinet cabinet = cabinetService.findByIdWithUserOrThrow(event.getCabinet().getId());
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            return WbApiEventExecutionResult.finalError("У кабинета отсутствует API ключ");
        }

        try {
            WbCardsListRequest request = buildRequest(payload);
            WbCardsListResponse response = contentApiClient.getCardsList(cabinet.getApiKey(), request);
            productCardService.saveOrUpdateCards(response, cabinet);

            if (hasMore(response)) {
                WbContentCardsListPagePayload nextPayload = WbContentCardsListPagePayload.builder()
                        .dateFrom(payload.dateFrom())
                        .dateTo(payload.dateTo())
                        .includeStocks(payload.includeStocks())
                        .cursorNmId(response.getCursor().getNmID())
                        .cursorUpdatedAt(response.getCursor().getUpdatedAt())
                        .build();
                eventService.enqueueNextContentEvent(cabinet.getId(), nextPayload, event.getTriggerSource());
                return WbApiEventExecutionResult.completedSuccessfully();
            }

            enqueueAnalyticsEvents(cabinet.getId(), payload, event.getTriggerSource());
            return WbApiEventExecutionResult.completedSuccessfully();
        } catch (Exception e) {
            WbApiEventExecutionResult deferOrRetry = WbEventExecutionErrors.wrapDeferOrRetryable(e);
            if (deferOrRetry.deferUntil() != null) {
                return deferOrRetry;
            }
            boolean hasCardsInDb = !productCardService.findByCabinetId(cabinet.getId()).isEmpty();
            boolean retriesExhausted = event.getAttemptCount() + 1 >= event.getMaxAttempts();
            if (retriesExhausted && hasCardsInDb) {
                log.warn("CONTENT retries исчерпаны для кабинета {}, используем fallback по существующим карточкам", cabinet.getId());
                enqueueAnalyticsEvents(cabinet.getId(), payload, event.getTriggerSource());
                cabinetUpdateErrorService.recordError(cabinet.getId(), CabinetUpdateErrorScope.MAIN,
                        "CONTENT fallback: " + e.getMessage());
                return WbApiEventExecutionResult.fallbackSuccess("CONTENT fallback использован: " + e.getMessage());
            }
            cabinetUpdateErrorService.recordError(cabinet.getId(), CabinetUpdateErrorScope.MAIN, e.getMessage());
            return WbApiEventExecutionResult.retryableError(e.getMessage());
        }
    }

    private WbCardsListRequest buildRequest(WbContentCardsListPagePayload payload) {
        WbCardsListRequest.Cursor cursor = WbCardsListRequest.Cursor.builder()
                .limit(CARDS_PAGE_LIMIT)
                .nmID(payload.cursorNmId())
                .updatedAt(payload.cursorUpdatedAt())
                .build();
        WbCardsListRequest.Filter filter = WbCardsListRequest.Filter.builder()
                .withPhoto(WITH_PHOTO_ALL)
                .build();
        return WbCardsListRequest.builder()
                .settings(WbCardsListRequest.Settings.builder()
                        .cursor(cursor)
                        .filter(filter)
                        .build())
                .build();
    }

    private boolean hasMore(WbCardsListResponse response) {
        if (response.getCursor() == null || response.getCursor().getTotal() == null) {
            return false;
        }
        return response.getCursor().getTotal() >= CARDS_PAGE_LIMIT
                && response.getCursor().getNmID() != null
                && response.getCursor().getUpdatedAt() != null;
    }

    private void enqueueAnalyticsEvents(Long cabinetId, WbContentCardsListPagePayload payload, String triggerSource) {
        WbMainStepPayload mainStepPayload = WbMainStepPayload.builder()
                .dateFrom(payload.dateFrom())
                .dateTo(payload.dateTo())
                .includeStocks(payload.includeStocks())
                .build();
        LocalDate syncTo = payload.dateTo() != null ? payload.dateTo() : LocalDate.now().minusDays(1);
        WbMainStepPayload promotionStepPayload = WbMainStepPayload.builder()
                .dateFrom(WbSyncPeriods.promotionFrom(syncTo))
                .dateTo(syncTo)
                .includeStocks(payload.includeStocks())
                .build();
        WbMainStepPayload funnelStepPayload = WbMainStepPayload.builder()
                .dateFrom(WbSyncPeriods.funnelFrom(syncTo))
                .dateTo(syncTo)
                .includeStocks(payload.includeStocks())
                .build();
        eventService.enqueuePricesRequestLevelEvents(cabinetId, mainStepPayload, triggerSource);
        eventService.enqueuePromotionRequestLevelEvents(cabinetId, promotionStepPayload, triggerSource);
        eventService.enqueueItemRatingSyncCabinetEvent(cabinetId, mainStepPayload, triggerSource);
        eventService.enqueuePromotionCalendarSyncCabinetEvent(cabinetId, mainStepPayload, triggerSource);

        List<Long> nmIds = productCardService.findByCabinetId(cabinetId).stream()
                .map(WbProductCard::getNmId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (payload.includeStocks()) {
            eventService.enqueueAllStocksByNmIdForCabinet(cabinetId, triggerSource);
        }
        for (Long nmId : nmIds) {
            eventService.enqueueAnalyticsSalesFunnelEvent(
                    cabinetId,
                    nmId,
                    funnelStepPayload.dateFrom(),
                    funnelStepPayload.dateTo(),
                    payload.includeStocks(),
                    triggerSource
            );
        }
    }
}
