package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.oparin.solution.exception.WbApiUnauthorizedScopeException;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.service.CabinetScopeStatusService;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.events.payload.FeedbacksSyncStepPayload;
import ru.oparin.solution.service.events.payload.MainStepPayload;
import ru.oparin.solution.service.sync.FeedbacksSyncService;

@Component("feedbacksSyncCabinetEventExecutor")
@RequiredArgsConstructor
@Slf4j
public class FeedbacksSyncCabinetEventExecutor implements WbApiEventExecutor {

    private static final String MSG_SCOPE = "Для кабинета {} нет доступа к категории WB API: {}.";

    private final WbApiEventService eventService;
    private final CabinetService cabinetService;
    private final FeedbacksSyncService feedbacksSyncService;
    private final CabinetScopeStatusService cabinetScopeStatusService;

    @Override
    public WbApiEventExecutionResult execute(WbApiEvent event) {
        FeedbacksSyncStepPayload payload = null;
        MainStepPayload legacyPayload = null;
        try {
            payload = eventService.readPayload(event, FeedbacksSyncStepPayload.class);
            if (payload == null || payload.runId() == null) {
                legacyPayload = eventService.readPayload(event, MainStepPayload.class);
            }
        } catch (Exception ignored) {
            legacyPayload = eventService.readPayload(event, MainStepPayload.class);
        }
        var cabinet = cabinetService.findByIdWithUserOrThrow(event.getCabinet().getId());
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            return WbApiEventExecutionResult.finalError("У кабинета отсутствует API ключ");
        }
        if (legacyPayload != null) {
            try {
                feedbacksSyncService.syncFeedbacksForCabinetInNewTransaction(cabinet, cabinet.getApiKey());
            } catch (WbApiUnauthorizedScopeException e) {
                cabinetScopeStatusService.recordFailure(cabinet.getId(), e.getCategory(), e.getMessage());
                log.warn(MSG_SCOPE, cabinet.getId(), e.getCategory().getDisplayName());
            } catch (Exception e) {
                WbApiEventExecutionResult deferOrRetry = WbEventExecutionErrors.wrapDeferOrRetryable(e);
                if (deferOrRetry.deferUntil() != null) {
                    return deferOrRetry;
                }
                log.warn("Legacy sync отзывов для кабинета {} завершился с ошибкой: {}", cabinet.getId(), e.getMessage());
            }
            if (!isAdminBulkStandalone(event.getTriggerSource())) {
                eventService.tryFinalizeMain(cabinet.getId(), event.getId());
            }
            return WbApiEventExecutionResult.completedSuccessfully();
        }
        try {
            FeedbacksSyncService.FeedbacksStepProcessingResult result =
                    feedbacksSyncService.processFeedbacksStepInNewTransaction(
                            cabinet,
                            cabinet.getApiKey(),
                            payload,
                            event.getTriggerSource()
                    );
            log.info("Шаг синхронизации отзывов завершён: eventId={}, runId={}, cabinetId={}, completedRun={}",
                    event.getId(), payload.runId(), cabinet.getId(), result.completedRun());
            if (result.completedRun() && !isAdminBulkStandalone(event.getTriggerSource())) {
                eventService.tryFinalizeMain(cabinet.getId(), event.getId());
            }
        } catch (WbApiUnauthorizedScopeException e) {
            eventService.markFeedbacksRunFailed(payload.runId(), e.getMessage());
            cabinetScopeStatusService.recordFailure(cabinet.getId(), e.getCategory(), e.getMessage());
            log.warn(MSG_SCOPE, cabinet.getId(), e.getCategory().getDisplayName());
        } catch (Exception e) {
            eventService.markFeedbacksRunFailed(payload.runId(), e.getMessage());
            WbApiEventExecutionResult deferOrRetry = WbEventExecutionErrors.wrapDeferOrRetryable(e);
            if (deferOrRetry.deferUntil() != null) {
                return deferOrRetry;
            }
            log.warn("Синхронизация отзывов для кабинета {} завершилась с ошибкой: {}", cabinet.getId(), e.getMessage());
        }
        return WbApiEventExecutionResult.completedSuccessfully();
    }

    private static boolean isAdminBulkStandalone(String triggerSource) {
        return triggerSource != null && triggerSource.startsWith("ADMIN_BULK");
    }
}
