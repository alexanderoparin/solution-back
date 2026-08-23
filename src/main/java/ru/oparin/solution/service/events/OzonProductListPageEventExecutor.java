package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import ru.oparin.solution.dto.ozon.OzonProductInfoListResponse;
import ru.oparin.solution.dto.ozon.OzonProductListResponse;
import ru.oparin.solution.exception.OzonRateLimitDeferException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.CabinetUpdateErrorScope;
import ru.oparin.solution.model.OzonApiEvent;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.CabinetUpdateErrorService;
import ru.oparin.solution.service.OzonProductCardService;
import ru.oparin.solution.service.events.payload.OzonProductListPagePayload;
import ru.oparin.solution.service.ozon.OzonProductsApiClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Component("ozonProductListPageEventExecutor")
@RequiredArgsConstructor
@Slf4j
public class OzonProductListPageEventExecutor implements OzonApiEventExecutor {

    private static final int PAGE_LIMIT = 100;

    private final OzonApiEventService eventService;
    private final CabinetService cabinetService;
    private final OzonProductsApiClient productsApiClient;
    private final OzonProductCardService productCardService;
    private final CabinetUpdateErrorService cabinetUpdateErrorService;

    @Override
    public OzonApiEventExecutionResult execute(OzonApiEvent event) {
        OzonProductListPagePayload payload = eventService.readPayload(event, OzonProductListPagePayload.class);
        Cabinet cabinet = cabinetService.findByIdWithUserOrThrow(event.getCabinet().getId());
        String clientId = cabinet.getOzonClientId();
        String apiKey = cabinet.getApiKey();
        if (clientId == null || clientId.isBlank() || apiKey == null || apiKey.isBlank()) {
            return OzonApiEventExecutionResult.finalError("У Ozon-кабинета не заданы Client-Id или Api-Key");
        }

        try {
            String lastId = payload.lastId() != null ? payload.lastId() : "";
            OzonProductListResponse listResponse = productsApiClient.listProducts(clientId, apiKey, lastId, PAGE_LIMIT);
            List<Long> productIds = extractProductIds(listResponse);
            OzonProductInfoListResponse infoResponse = productIds.isEmpty()
                    ? null
                    : productsApiClient.getProductInfoList(clientId, apiKey, productIds);
            productCardService.saveOrUpdateProducts(cabinet, listResponse, infoResponse);

            String nextLastId = listResponse.getResult() != null ? listResponse.getResult().getLastId() : null;
            if (hasMore(listResponse, nextLastId)) {
                OzonProductListPagePayload nextPayload = OzonProductListPagePayload.builder()
                        .lastId(nextLastId)
                        .includeStocks(payload.includeStocks())
                        .build();
                eventService.enqueueNextProductListEvent(cabinet.getId(), nextPayload, event.getTriggerSource());
                return OzonApiEventExecutionResult.completedSuccessfully();
            }

            eventService.enqueuePricesCabinetEvent(cabinet.getId(), payload.includeStocks(), event.getTriggerSource());
            eventService.enqueueContentRatingCabinetEvent(cabinet.getId(), event.getTriggerSource());
            log.info("Ozon каталог полностью загружен для cabinetId={}, поставлены цены и контент-рейтинг", cabinet.getId());
            return OzonApiEventExecutionResult.completedSuccessfully();
        } catch (OzonRateLimitDeferException e) {
            return OzonApiEventExecutionResult.deferredRetry(
                    e.getMessage(),
                    e.getDeferUntil() != null ? e.getDeferUntil() : LocalDateTime.now().plusSeconds(60)
            );
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                return OzonApiEventExecutionResult.deferredRetry(
                        "Rate limit Ozon API",
                        LocalDateTime.now().plusSeconds(60)
                );
            }
            cabinetUpdateErrorService.recordError(cabinet.getId(), CabinetUpdateErrorScope.MAIN, e.getMessage());
            return OzonApiEventExecutionResult.retryableError("Ozon API: " + e.getStatusCode() + " " + e.getMessage());
        } catch (Exception e) {
            OzonRateLimitDeferException defer = OzonRateLimitDeferException.findInChain(e);
            if (defer != null) {
                return OzonApiEventExecutionResult.deferredRetry(
                        defer.getMessage(),
                        defer.getDeferUntil() != null ? defer.getDeferUntil() : LocalDateTime.now().plusSeconds(60)
                );
            }
            cabinetUpdateErrorService.recordError(cabinet.getId(), CabinetUpdateErrorScope.MAIN, e.getMessage());
            return OzonApiEventExecutionResult.retryableError(e.getMessage());
        }
    }

    private static List<Long> extractProductIds(OzonProductListResponse listResponse) {
        if (listResponse == null || listResponse.getResult() == null || listResponse.getResult().getItems() == null) {
            return List.of();
        }
        return listResponse.getResult().getItems().stream()
                .map(OzonProductListResponse.Item::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private static boolean hasMore(OzonProductListResponse listResponse, String nextLastId) {
        if (nextLastId == null || nextLastId.isBlank()) {
            return false;
        }
        if (listResponse.getResult() == null || listResponse.getResult().getItems() == null) {
            return false;
        }
        return !listResponse.getResult().getItems().isEmpty();
    }
}
