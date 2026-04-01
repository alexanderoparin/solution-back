package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.oparin.solution.exception.WbApiUnauthorizedScopeException;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.service.CabinetScopeStatusService;
import ru.oparin.solution.service.CabinetService;
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
        MainStepPayload payload = eventService.readPayload(event, MainStepPayload.class);
        var cabinet = cabinetService.findByIdWithUserOrThrow(event.getCabinet().getId());
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            return WbApiEventExecutionResult.finalError("У кабинета отсутствует API ключ");
        }
        try {
            feedbacksSyncService.syncFeedbacksForCabinetInNewTransaction(cabinet, cabinet.getApiKey());
        } catch (WbApiUnauthorizedScopeException e) {
            cabinetScopeStatusService.recordFailure(cabinet.getId(), e.getCategory(), e.getMessage());
            log.warn(MSG_SCOPE, cabinet.getId(), e.getCategory().getDisplayName());
        } catch (Exception e) {
            log.warn("Синхронизация отзывов для кабинета {} завершилась с ошибкой: {}", cabinet.getId(), e.getMessage());
        }
        if (!isAdminBulkStandalone(event.getTriggerSource())) {
            eventService.tryFinalizeMain(cabinet.getId(), payload.includeStocks(), event.getTriggerSource(), event.getId());
        }
        return WbApiEventExecutionResult.completedSuccessfully();
    }

    private static boolean isAdminBulkStandalone(String triggerSource) {
        return triggerSource != null && triggerSource.startsWith("ADMIN_BULK");
    }
}
