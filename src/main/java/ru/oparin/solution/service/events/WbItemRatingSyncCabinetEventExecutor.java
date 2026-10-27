package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.events.payload.WbItemRatingSyncStepPayload;
import ru.oparin.solution.service.events.payload.WbMainStepPayload;
import ru.oparin.solution.service.sync.WbItemRatingSyncService;

/**
 * Событие sync рейтинга карточек: публичный catalog {@code reviewRating}, батчами через offset.
 */
@Component("itemRatingSyncCabinetEventExecutor")
@RequiredArgsConstructor
@Slf4j
public class WbItemRatingSyncCabinetEventExecutor implements WbApiEventExecutor {

    private final WbApiEventService eventService;
    private final CabinetService cabinetService;
    private final WbItemRatingSyncService itemRatingSyncService;

    @Override
    public WbApiEventExecutionResult execute(WbApiEvent event) {
        WbItemRatingSyncStepPayload stepPayload = null;
        WbMainStepPayload legacyPayload = null;
        try {
            stepPayload = eventService.readPayload(event, WbItemRatingSyncStepPayload.class);
            if (stepPayload == null || stepPayload.syncStartedAt() == null) {
                legacyPayload = eventService.readPayload(event, WbMainStepPayload.class);
            }
        } catch (Exception ignored) {
            legacyPayload = eventService.readPayload(event, WbMainStepPayload.class);
        }

        var cabinet = cabinetService.findByIdWithUserOrThrow(event.getCabinet().getId());
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            // API-ключ кабинета для публичного рейтинга не нужен, но кабинет без ключа
            // обычно не участвует в основной волне обновления.
            return WbApiEventExecutionResult.finalError("У кабинета отсутствует API ключ");
        }

        if (legacyPayload != null) {
            try {
                itemRatingSyncService.syncForCabinetInNewTransaction(cabinet, cabinet.getApiKey());
            } catch (Exception e) {
                WbApiEventExecutionResult deferOrRetry = WbEventExecutionErrors.wrapDeferOrRetryable(e);
                if (deferOrRetry.deferUntil() != null) {
                    return deferOrRetry;
                }
                log.warn("Legacy sync рейтинга (public catalog) для кабинета {} завершился с ошибкой: {}",
                        cabinet.getId(), e.getMessage());
            }
            if (!isAdminBulkStandalone(event.getTriggerSource())) {
                eventService.tryFinalizeMain(cabinet.getId(), event.getId());
            }
            return WbApiEventExecutionResult.completedSuccessfully();
        }

        try {
            WbItemRatingSyncService.ItemRatingStepProcessingResult result =
                    itemRatingSyncService.processStepInNewTransaction(
                            cabinet,
                            cabinet.getApiKey(),
                            stepPayload,
                            event.getTriggerSource()
                    );
            log.info("Шаг синхронизации рейтинга (public catalog) завершён: eventId={}, cabinetId={}, completedRun={}",
                    event.getId(), cabinet.getId(), result.completedRun());
            if (result.completedRun() && !isAdminBulkStandalone(event.getTriggerSource())) {
                eventService.tryFinalizeMain(cabinet.getId(), event.getId());
            }
        } catch (Exception e) {
            WbApiEventExecutionResult deferOrRetry = WbEventExecutionErrors.wrapDeferOrRetryable(e);
            if (deferOrRetry.deferUntil() != null) {
                return deferOrRetry;
            }
            log.warn("Синхронизация рейтинга (public catalog) для кабинета {} завершилась с ошибкой: {}",
                    cabinet.getId(), e.getMessage());
        }
        return WbApiEventExecutionResult.completedSuccessfully();
    }

    private static boolean isAdminBulkStandalone(String triggerSource) {
        return triggerSource != null && triggerSource.startsWith("ADMIN_BULK");
    }
}
