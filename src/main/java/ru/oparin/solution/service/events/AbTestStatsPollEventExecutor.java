package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import ru.oparin.solution.exception.WbRateLimitDeferException;
import ru.oparin.solution.model.AbTest;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.repository.AbTestRepository;
import ru.oparin.solution.service.abtest.AbTestService;
import ru.oparin.solution.service.abtest.AbTestStatsService;
import ru.oparin.solution.service.events.payload.AbTestStatsPollPayload;

/**
 * Асинхронный опрос fullstats и атрибуция дельт А/Б-теста.
 */
@Component("abTestStatsPollEventExecutor")
@RequiredArgsConstructor
@Slf4j
public class AbTestStatsPollEventExecutor implements WbApiEventExecutor {

    private final WbApiEventService eventService;
    private final AbTestRepository abTestRepository;
    private final AbTestStatsService abTestStatsService;
    private final AbTestService abTestService;

    @Override
    public WbApiEventExecutionResult execute(WbApiEvent event) {
        AbTestStatsPollPayload payload = eventService.readPayload(event, AbTestStatsPollPayload.class);
        if (payload.abTestId() == null) {
            return WbApiEventExecutionResult.finalError("Не указан id А/Б-теста");
        }
        AbTest test = abTestRepository.findById(payload.abTestId()).orElse(null);
        if (test == null) {
            return WbApiEventExecutionResult.finalError("А/Б-тест не найден");
        }
        try {
            abTestStatsService.pollOne(test);
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
            return WbEventExecutionErrors.wrapRestClientException(e);
        } catch (Exception e) {
            abTestService.markWbError(payload.abTestId(), e.getMessage());
            return WbEventExecutionErrors.wrapDeferOrRetryable(e);
        }
    }
}
