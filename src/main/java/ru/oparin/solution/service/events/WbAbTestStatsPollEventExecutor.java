package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import ru.oparin.solution.exception.WbRateLimitDeferException;
import ru.oparin.solution.model.WbAbTest;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.repository.WbAbTestRepository;
import ru.oparin.solution.service.abtest.WbAbTestService;
import ru.oparin.solution.service.abtest.WbAbTestStatsService;
import ru.oparin.solution.service.events.payload.WbAbTestStatsPollPayload;

/**
 * Асинхронный опрос fullstats и атрибуция дельт А/Б-теста.
 */
@Component("abTestStatsPollEventExecutor")
@RequiredArgsConstructor
@Slf4j
public class WbAbTestStatsPollEventExecutor implements WbApiEventExecutor {

    private final WbApiEventService eventService;
    private final WbAbTestRepository abTestRepository;
    private final WbAbTestStatsService abTestStatsService;
    private final WbAbTestService abTestService;

    @Override
    public WbApiEventExecutionResult execute(WbApiEvent event) {
        WbAbTestStatsPollPayload payload = eventService.readPayload(event, WbAbTestStatsPollPayload.class);
        if (payload.abTestId() == null) {
            return WbApiEventExecutionResult.finalError("Не указан id А/Б-теста");
        }
        WbAbTest test = abTestRepository.findById(payload.abTestId()).orElse(null);
        if (test == null) {
            return WbApiEventExecutionResult.finalError("А/Б-тест не найден");
        }
        try {
            abTestStatsService.pollOne(test);
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
