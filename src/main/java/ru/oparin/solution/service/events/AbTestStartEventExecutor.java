package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import ru.oparin.solution.exception.WbRateLimitDeferException;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.service.abtest.AbTestService;
import ru.oparin.solution.service.events.payload.AbTestStartPayload;

/**
 * Асинхронный старт А/Б-теста: загрузка вариантов и выставление главного фото на WB.
 */
@Component("abTestStartEventExecutor")
@RequiredArgsConstructor
@Slf4j
public class AbTestStartEventExecutor implements WbApiEventExecutor {

    private final WbApiEventService eventService;
    private final AbTestService abTestService;

    @Override
    public WbApiEventExecutionResult execute(WbApiEvent event) {
        AbTestStartPayload payload = eventService.readPayload(event, AbTestStartPayload.class);
        if (payload.abTestId() == null) {
            return WbApiEventExecutionResult.finalError("Не указан id А/Б-теста");
        }
        try {
            abTestService.executeStart(payload.abTestId());
            return WbApiEventExecutionResult.completedSuccessfully();
        } catch (WbRateLimitDeferException e) {
            abTestService.markWbError(payload.abTestId(), e.getMessage());
            return WbEventExecutionErrors.fromDeferException(e);
        } catch (RestClientException e) {
            WbApiEventExecutionResult defer = WbEventExecutionErrors.deferResultIfPresent(e);
            if (defer != null) {
                abTestService.markWbError(payload.abTestId(), e.getMessage());
                return defer;
            }
            abTestService.markWbError(payload.abTestId(), e.getMessage());
            if (event.getAttemptCount() != null && event.getMaxAttempts() != null
                    && event.getAttemptCount() + 1 >= event.getMaxAttempts()) {
                abTestService.failStart(payload.abTestId(), e.getMessage());
                return WbApiEventExecutionResult.finalError(e.getMessage());
            }
            return WbEventExecutionErrors.wrapRestClientException(e);
        } catch (Exception e) {
            abTestService.markWbError(payload.abTestId(), e.getMessage());
            return WbEventExecutionErrors.wrapDeferOrRetryable(e);
        }
    }
}
