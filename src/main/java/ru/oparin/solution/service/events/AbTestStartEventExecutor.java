package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import ru.oparin.solution.exception.WbApiUnauthorizedScopeException;
import ru.oparin.solution.exception.WbRateLimitDeferException;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.service.abtest.AbTestService;
import ru.oparin.solution.service.events.payload.AbTestStartPayload;

/**
 * Асинхронный старт А/Б-теста: resolve → (restore, если галерею уже затёрли) → apply control в слот 1.
 * Каждый шаг коммитится отдельно, чтобы defer rate-limit не откатывал уже сделанную работу.
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
            Long cabinetId = event.getCabinet() != null ? event.getCabinet().getId() : null;
            if (cabinetId == null) {
                return WbApiEventExecutionResult.finalError("Не указан кабинет события А/Б-старта");
            }
            abTestService.processStartStepInNewTransaction(
                    cabinetId,
                    payload,
                    event.getTriggerSource() != null ? event.getTriggerSource() : "AB_TEST_START"
            );
            return WbApiEventExecutionResult.completedSuccessfully();
        } catch (WbRateLimitDeferException e) {
            // Отложенный повтор — не ошибка для UI
            return WbEventExecutionErrors.fromDeferException(e);
        } catch (WbApiUnauthorizedScopeException e) {
            // 401 / токен без нужной категории — без ретраев, сразу failStart
            return failStartDueToToken(payload.abTestId(), e);
        } catch (RestClientException e) {
            WbApiEventExecutionResult defer = WbEventExecutionErrors.deferResultIfPresent(e);
            if (defer != null) {
                return defer;
            }
            if (AbTestService.isWbUnauthorizedTokenError(e)) {
                return failStartDueToToken(payload.abTestId(), e);
            }
            abTestService.markWbError(payload.abTestId(), e.getMessage());
            if (event.getAttemptCount() != null && event.getMaxAttempts() != null
                    && event.getAttemptCount() + 1 >= event.getMaxAttempts()) {
                abTestService.failStart(payload.abTestId(), e.getMessage());
                return WbApiEventExecutionResult.finalError(e.getMessage());
            }
            return WbEventExecutionErrors.wrapRestClientException(e);
        } catch (Exception e) {
            WbApiEventExecutionResult defer = WbEventExecutionErrors.deferResultIfPresent(e);
            if (defer != null) {
                return defer;
            }
            if (AbTestService.isWbUnauthorizedTokenError(e)) {
                return failStartDueToToken(payload.abTestId(), e);
            }
            abTestService.markWbError(payload.abTestId(), e.getMessage());
            if (event.getAttemptCount() != null && event.getMaxAttempts() != null
                    && event.getAttemptCount() + 1 >= event.getMaxAttempts()) {
                abTestService.failStart(payload.abTestId(), e.getMessage());
                return WbApiEventExecutionResult.finalError(e.getMessage());
            }
            return WbEventExecutionErrors.wrapDeferOrRetryable(e);
        }
    }

    private WbApiEventExecutionResult failStartDueToToken(Long abTestId, Throwable cause) {
        String message = AbTestService.TOKEN_CONTENT_WRITE_REQUIRED;
        log.warn("А/Б-старт testId={}: отказ токена WB ({}), завершаем PENDING_START", abTestId, cause.getMessage());
        abTestService.failStart(abTestId, message);
        return WbApiEventExecutionResult.finalError(message);
    }
}
