package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import ru.oparin.solution.exception.WbRateLimitDeferException;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.service.abtest.AbTestService;
import ru.oparin.solution.service.events.payload.AbTestApplyPhotoPayload;

/**
 * Асинхронная смена главного фото А/Б-теста (ротация или завершение).
 */
@Component("abTestApplyPhotoEventExecutor")
@RequiredArgsConstructor
@Slf4j
public class AbTestApplyPhotoEventExecutor implements WbApiEventExecutor {

    private final WbApiEventService eventService;
    private final AbTestService abTestService;

    @Override
    public WbApiEventExecutionResult execute(WbApiEvent event) {
        AbTestApplyPhotoPayload payload = eventService.readPayload(event, AbTestApplyPhotoPayload.class);
        if (payload.abTestId() == null || payload.variantId() == null) {
            return WbApiEventExecutionResult.finalError("Не указаны id теста или варианта");
        }
        try {
            abTestService.executeApplyPhoto(
                    payload.abTestId(),
                    payload.variantId(),
                    payload.reason(),
                    payload.finishAfterApply()
            );
            return WbApiEventExecutionResult.completedSuccessfully();
        } catch (WbRateLimitDeferException e) {
            return WbEventExecutionErrors.fromDeferException(e);
        } catch (RestClientException e) {
            WbApiEventExecutionResult defer = WbEventExecutionErrors.deferResultIfPresent(e);
            if (defer != null) {
                return defer;
            }
            abTestService.markWbError(payload.abTestId(), e.getMessage());
            return WbEventExecutionErrors.wrapRestClientException(e);
        } catch (Exception e) {
            WbApiEventExecutionResult defer = WbEventExecutionErrors.deferResultIfPresent(e);
            if (defer != null) {
                return defer;
            }
            abTestService.markWbError(payload.abTestId(), e.getMessage());
            return WbEventExecutionErrors.wrapDeferOrRetryable(e);
        }
    }
}
