package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import ru.oparin.solution.exception.WbApiUnauthorizedScopeException;
import ru.oparin.solution.exception.WbRateLimitDeferException;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.service.abtest.WbAbTestService;
import ru.oparin.solution.service.events.payload.WbAbTestApplyPhotoPayload;

/**
 * Асинхронная смена главного фото А/Б-теста (ротация или завершение).
 */
@Component("abTestApplyPhotoEventExecutor")
@RequiredArgsConstructor
@Slf4j
public class WbAbTestApplyPhotoEventExecutor implements WbApiEventExecutor {

    private final WbApiEventService eventService;
    private final WbAbTestService abTestService;

    @Override
    public WbApiEventExecutionResult execute(WbApiEvent event) {
        WbAbTestApplyPhotoPayload payload = eventService.readPayload(event, WbAbTestApplyPhotoPayload.class);
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
        } catch (WbApiUnauthorizedScopeException e) {
            abTestService.markWbError(payload.abTestId(), WbAbTestService.TOKEN_CONTENT_WRITE_REQUIRED);
            return WbApiEventExecutionResult.finalError(WbAbTestService.TOKEN_CONTENT_WRITE_REQUIRED);
        } catch (RestClientException e) {
            WbApiEventExecutionResult defer = WbEventExecutionErrors.deferResultIfPresent(e);
            if (defer != null) {
                return defer;
            }
            if (WbAbTestService.isWbUnauthorizedTokenError(e)) {
                abTestService.markWbError(payload.abTestId(), WbAbTestService.TOKEN_CONTENT_WRITE_REQUIRED);
                return WbApiEventExecutionResult.finalError(WbAbTestService.TOKEN_CONTENT_WRITE_REQUIRED);
            }
            abTestService.markWbError(payload.abTestId(), e.getMessage());
            return WbEventExecutionErrors.wrapRestClientException(e);
        } catch (Exception e) {
            WbApiEventExecutionResult defer = WbEventExecutionErrors.deferResultIfPresent(e);
            if (defer != null) {
                return defer;
            }
            if (WbAbTestService.isWbUnauthorizedTokenError(e)) {
                abTestService.markWbError(payload.abTestId(), WbAbTestService.TOKEN_CONTENT_WRITE_REQUIRED);
                return WbApiEventExecutionResult.finalError(WbAbTestService.TOKEN_CONTENT_WRITE_REQUIRED);
            }
            abTestService.markWbError(payload.abTestId(), e.getMessage());
            return WbEventExecutionErrors.wrapDeferOrRetryable(e);
        }
    }
}
