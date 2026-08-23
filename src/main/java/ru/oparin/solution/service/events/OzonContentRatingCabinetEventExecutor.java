package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import ru.oparin.solution.exception.OzonRateLimitDeferException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.CabinetUpdateErrorScope;
import ru.oparin.solution.model.OzonApiEvent;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.CabinetUpdateErrorService;
import ru.oparin.solution.service.events.payload.OzonContentRatingCabinetPayload;
import ru.oparin.solution.service.sync.OzonContentRatingSyncService;

import java.time.LocalDateTime;

/**
 * Пошаговая синхронизация контент-рейтинга Ozon по SKU кабинета.
 */
@Component("ozonContentRatingCabinetEventExecutor")
@RequiredArgsConstructor
@Slf4j
public class OzonContentRatingCabinetEventExecutor implements OzonApiEventExecutor {

    private final OzonApiEventService eventService;
    private final CabinetService cabinetService;
    private final OzonContentRatingSyncService contentRatingSyncService;
    private final CabinetUpdateErrorService cabinetUpdateErrorService;

    @Override
    public OzonApiEventExecutionResult execute(OzonApiEvent event) {
        OzonContentRatingCabinetPayload payload = eventService.readPayload(event, OzonContentRatingCabinetPayload.class);
        Cabinet cabinet = cabinetService.findByIdWithUserOrThrow(event.getCabinet().getId());
        String clientId = cabinet.getOzonClientId();
        String apiKey = cabinet.getApiKey();
        if (clientId == null || clientId.isBlank() || apiKey == null || apiKey.isBlank()) {
            return OzonApiEventExecutionResult.finalError("У Ozon-кабинета не заданы Client-Id или Api-Key");
        }

        OzonContentRatingCabinetPayload step = payload != null
                ? payload
                : OzonContentRatingCabinetPayload.builder()
                .offset(0)
                .syncStartedAt(LocalDateTime.now())
                .build();

        try {
            OzonContentRatingSyncService.ContentRatingStepResult result = contentRatingSyncService.processStep(
                    cabinet, clientId.trim(), apiKey.trim(), step, event.getTriggerSource());
            log.info("Ozon content-rating step: cabinetId={}, offset={}, completed={}, updated={}",
                    cabinet.getId(), step.offset(), result.completedRun(), result.updatedCount());
            return OzonApiEventExecutionResult.completedSuccessfully();
        } catch (OzonRateLimitDeferException e) {
            return OzonApiEventExecutionResult.deferredRetry(
                    e.getMessage(),
                    e.getDeferUntil() != null ? e.getDeferUntil() : LocalDateTime.now().plusSeconds(60)
            );
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                return OzonApiEventExecutionResult.deferredRetry(
                        "Rate limit Ozon API",
                        LocalDateTime.now().plusSeconds(60)
                );
            }
            cabinetUpdateErrorService.recordError(cabinet.getId(), CabinetUpdateErrorScope.MAIN, e.getMessage());
            return OzonApiEventExecutionResult.retryableError("Ozon API: " + e.getStatusCode());
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
