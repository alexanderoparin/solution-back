package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.sync.OzonPromotionCampaignSyncService;

/**
 * Загрузка списка рекламных кампаний Ozon Performance API по кабинету.
 */
@Component("ozonCampaignsCabinetEventExecutor")
@RequiredArgsConstructor
@Slf4j
public class OzonCampaignsCabinetEventExecutor implements OzonApiEventExecutor {

    private final OzonApiEventService eventService;
    private final CabinetService cabinetService;
    private final OzonPromotionCampaignSyncService campaignSyncService;

    @Override
    public OzonApiEventExecutionResult execute(ru.oparin.solution.model.OzonApiEvent event) {
        Cabinet cabinet = cabinetService.findByIdWithUserOrThrow(event.getCabinet().getId());
        String clientId = cabinet.getOzonPerformanceClientId();
        String clientSecret = cabinet.getOzonPerformanceClientSecret();
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            return OzonApiEventExecutionResult.finalError(
                    "У Ozon-кабинета не заданы Performance client_id или client_secret");
        }

        try {
            int count = campaignSyncService.syncCampaigns(cabinet, clientId.trim(), clientSecret.trim());
            eventService.markCampaignsSyncCompleted(cabinet.getId());
            log.info("Ozon campaigns sync завершён для cabinetId={}, кампаний={}", cabinet.getId(), count);
            return OzonApiEventExecutionResult.completedSuccessfully();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                return OzonApiEventExecutionResult.deferredRetry(
                        "Rate limit Ozon Performance API",
                        java.time.LocalDateTime.now().plusSeconds(60)
                );
            }
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                return OzonApiEventExecutionResult.finalError(
                        "Ozon Performance API: невалидные credentials (HTTP " + e.getStatusCode().value() + ")");
            }
            return OzonApiEventExecutionResult.retryableError("Ozon Performance API: " + e.getStatusCode());
        } catch (Exception e) {
            return OzonApiEventExecutionResult.retryableError(e.getMessage());
        }
    }
}
