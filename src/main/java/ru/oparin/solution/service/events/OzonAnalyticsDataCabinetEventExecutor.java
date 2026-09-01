package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import ru.oparin.solution.exception.OzonRateLimitDeferException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.CabinetUpdateErrorScope;
import ru.oparin.solution.model.OzonApiEvent;
import ru.oparin.solution.service.CabinetScopeStatusService;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.CabinetUpdateErrorService;
import ru.oparin.solution.service.OzonSellerSubscriptionService;
import ru.oparin.solution.service.events.payload.OzonAnalyticsDataCabinetPayload;
import ru.oparin.solution.service.ozon.OzonApiCategory;
import ru.oparin.solution.service.sync.OzonProductAnalyticsSyncService;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Загрузка аналитики продаж Ozon после каталога/цен/остатков.
 */
@Component("ozonAnalyticsDataCabinetEventExecutor")
@RequiredArgsConstructor
@Slf4j
public class OzonAnalyticsDataCabinetEventExecutor implements OzonApiEventExecutor {

    private final OzonApiEventService eventService;
    private final CabinetService cabinetService;
    private final OzonProductAnalyticsSyncService analyticsSyncService;
    private final CabinetUpdateErrorService cabinetUpdateErrorService;
    private final CabinetScopeStatusService cabinetScopeStatusService;
    private final OzonSellerSubscriptionService ozonSellerSubscriptionService;

    @Override
    public OzonApiEventExecutionResult execute(OzonApiEvent event) {
        OzonAnalyticsDataCabinetPayload payload = eventService.readPayload(event, OzonAnalyticsDataCabinetPayload.class);
        Cabinet cabinet = cabinetService.findByIdWithUserOrThrow(event.getCabinet().getId());
        String clientId = cabinet.getOzonClientId();
        String apiKey = cabinet.getApiKey();
        if (clientId == null || clientId.isBlank() || apiKey == null || apiKey.isBlank()) {
            return OzonApiEventExecutionResult.finalError("У Ozon-кабинета не заданы Client-Id или Api-Key");
        }

        LocalDate dateTo = payload != null && payload.dateTo() != null
                ? payload.dateTo()
                : LocalDate.now().minusDays(1);
        LocalDate dateFrom = payload != null && payload.dateFrom() != null
                ? payload.dateFrom()
                : dateTo.minusDays(13);

        try {
            try {
                ozonSellerSubscriptionService.refreshSellerInfoFromApi(cabinet);
            } catch (Exception e) {
                log.warn("Ozon subscription refresh failed for cabinetId={}: {}", cabinet.getId(), e.getMessage());
            }
            analyticsSyncService.syncAnalytics(cabinet, clientId, apiKey, dateFrom, dateTo);
            cabinetScopeStatusService.recordSuccess(cabinet.getId(), OzonApiCategory.ANALYTICS);
            eventService.markMainCompleted(cabinet.getId());
            log.info("Ozon analytics загружена, main завершён для cabinetId={}, период={}..{}",
                    cabinet.getId(), dateFrom, dateTo);
            return OzonApiEventExecutionResult.completedSuccessfully();
        } catch (OzonRateLimitDeferException e) {
            return OzonApiEventExecutionResult.deferredRetry(
                    e.getMessage(),
                    e.getDeferUntil() != null ? e.getDeferUntil() : LocalDateTime.now().plusSeconds(60)
            );
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                return OzonApiEventExecutionResult.deferredRetry(
                        "Rate limit Ozon API (analytics)",
                        LocalDateTime.now().plusSeconds(60)
                );
            }
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                cabinetScopeStatusService.recordFailure(cabinet.getId(), OzonApiCategory.ANALYTICS, e.getMessage());
            }
            // Без Premium часть метрик недоступна — для базовых revenue/ordered_units обычно ок;
            // 403/400 логируем как retryable, чтобы не ронять весь sync молча.
            cabinetUpdateErrorService.recordError(cabinet.getId(), CabinetUpdateErrorScope.MAIN, e.getMessage());
            return OzonApiEventExecutionResult.retryableError("Ozon API analytics: " + e.getStatusCode());
        } catch (Exception e) {
            OzonRateLimitDeferException defer = OzonRateLimitDeferException.findInChain(e);
            if (defer != null) {
                return OzonApiEventExecutionResult.deferredRetry(
                        defer.getMessage(),
                        defer.getDeferUntil() != null ? defer.getDeferUntil() : LocalDateTime.now().plusSeconds(60)
                );
            }
            cabinetUpdateErrorService.recordError(cabinet.getId(), CabinetUpdateErrorScope.MAIN, e.getMessage());
            return OzonApiEventExecutionResult.retryableError(e.getMessage());
        }
    }
}
