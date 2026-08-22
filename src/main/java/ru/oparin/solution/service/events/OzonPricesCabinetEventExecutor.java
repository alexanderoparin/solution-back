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
import ru.oparin.solution.service.events.payload.OzonPricesCabinetPayload;
import ru.oparin.solution.service.sync.OzonProductPricesSyncService;

import java.time.LocalDateTime;

@Component("ozonPricesCabinetEventExecutor")
@RequiredArgsConstructor
@Slf4j
public class OzonPricesCabinetEventExecutor implements OzonApiEventExecutor {

    private final OzonApiEventService eventService;
    private final CabinetService cabinetService;
    private final OzonProductPricesSyncService pricesSyncService;
    private final CabinetUpdateErrorService cabinetUpdateErrorService;

    @Override
    public OzonApiEventExecutionResult execute(OzonApiEvent event) {
        eventService.readPayload(event, OzonPricesCabinetPayload.class);
        Cabinet cabinet = cabinetService.findByIdWithUserOrThrow(event.getCabinet().getId());
        String clientId = cabinet.getOzonClientId();
        String apiKey = cabinet.getApiKey();
        if (clientId == null || clientId.isBlank() || apiKey == null || apiKey.isBlank()) {
            return OzonApiEventExecutionResult.finalError("У Ozon-кабинета не заданы Client-Id или Api-Key");
        }

        try {
            pricesSyncService.syncAllPrices(cabinet, clientId, apiKey);
            // TODO: includeStocks — отдельные события остатков Ozon
            eventService.markMainCompleted(cabinet.getId());
            log.info("Ozon цены загружены, main завершён для cabinetId={}", cabinet.getId());
            return OzonApiEventExecutionResult.completedSuccessfully();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                return OzonApiEventExecutionResult.deferredRetry(
                        "Rate limit Ozon API (prices)",
                        LocalDateTime.now().plusSeconds(60)
                );
            }
            cabinetUpdateErrorService.recordError(cabinet.getId(), CabinetUpdateErrorScope.MAIN, e.getMessage());
            return OzonApiEventExecutionResult.retryableError("Ozon API prices: " + e.getStatusCode());
        } catch (Exception e) {
            cabinetUpdateErrorService.recordError(cabinet.getId(), CabinetUpdateErrorScope.MAIN, e.getMessage());
            return OzonApiEventExecutionResult.retryableError(e.getMessage());
        }
    }
}
