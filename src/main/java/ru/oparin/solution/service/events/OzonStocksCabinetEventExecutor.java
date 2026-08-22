package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.CabinetUpdateErrorScope;
import ru.oparin.solution.model.OzonApiEvent;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.CabinetUpdateErrorService;
import ru.oparin.solution.service.sync.OzonProductStocksSyncService;

import java.time.LocalDateTime;

@Component("ozonStocksCabinetEventExecutor")
@RequiredArgsConstructor
@Slf4j
public class OzonStocksCabinetEventExecutor implements OzonApiEventExecutor {

    private final OzonApiEventService eventService;
    private final CabinetService cabinetService;
    private final OzonProductStocksSyncService stocksSyncService;
    private final CabinetUpdateErrorService cabinetUpdateErrorService;

    @Override
    public OzonApiEventExecutionResult execute(OzonApiEvent event) {
        Cabinet cabinet = cabinetService.findByIdWithUserOrThrow(event.getCabinet().getId());
        String clientId = cabinet.getOzonClientId();
        String apiKey = cabinet.getApiKey();
        if (clientId == null || clientId.isBlank() || apiKey == null || apiKey.isBlank()) {
            return OzonApiEventExecutionResult.finalError("У Ozon-кабинета не заданы Client-Id или Api-Key");
        }

        try {
            stocksSyncService.syncAllStocks(cabinet, clientId, apiKey);
            eventService.markMainCompleted(cabinet.getId());
            log.info("Ozon остатки загружены, main завершён для cabinetId={}", cabinet.getId());
            return OzonApiEventExecutionResult.completedSuccessfully();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                return OzonApiEventExecutionResult.deferredRetry(
                        "Rate limit Ozon API (stocks)",
                        LocalDateTime.now().plusSeconds(60)
                );
            }
            cabinetUpdateErrorService.recordError(cabinet.getId(), CabinetUpdateErrorScope.MAIN, e.getMessage());
            return OzonApiEventExecutionResult.retryableError("Ozon API stocks: " + e.getStatusCode());
        } catch (Exception e) {
            cabinetUpdateErrorService.recordError(cabinet.getId(), CabinetUpdateErrorScope.MAIN, e.getMessage());
            return OzonApiEventExecutionResult.retryableError(e.getMessage());
        }
    }
}
