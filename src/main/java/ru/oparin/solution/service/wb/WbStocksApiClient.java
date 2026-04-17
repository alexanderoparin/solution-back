package ru.oparin.solution.service.wb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import ru.oparin.solution.dto.wb.WbStocksSizesRequest;
import ru.oparin.solution.dto.wb.WbStocksSizesResponse;

import java.util.concurrent.Callable;

/**
 * Клиент для работы с API остатков товаров (analytics-api).
 * Эндпоинты: получение остатков товаров по размерам на складах WB.
 * Категория WB API: Аналитика.
 */
@Service
@Slf4j
public class WbStocksApiClient extends AbstractWbApiClient {

    @Override
    protected WbApiCategory getApiCategory() {
        return WbApiCategory.ANALYTICS;
    }

    private static final String STOCKS_SIZES_ENDPOINT = "/api/v2/stocks-report/products/sizes";
    private static final String STOCKS_SIZES_OPERATION = "остатки по размерам";

    @Value("${wb.api.analytics-base-url}")
    private String analyticsBaseUrl;

    /**
     * Получение остатков товаров по размерам на складах WB.
     * При 429 повтор не выполняется в текущем потоке: событие уходит в retry через event-очередь.
     * Ретраи при 504/таймауте/соединении — в базовом клиенте.
     */
    public WbStocksSizesResponse getWbStocksBySizes(String apiKey, WbStocksSizesRequest request) {
        HttpHeaders headers = createAuthHeaders(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<WbStocksSizesRequest> entity = new HttpEntity<>(request, headers);
        String url = analyticsBaseUrl + STOCKS_SIZES_ENDPOINT;
        logWbApiCall(url, "остатки по размерам на складах WB", request.getNmID());

        String context = "запрос остатков для nmID " + request.getNmID();
        Callable<WbStocksSizesResponse> oneAttempt = () -> {
            ResponseEntity<WbStocksSizesResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    WbStocksSizesResponse.class
            );
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Ошибка от WB API: статус={}", response.getStatusCode());
                throw new RestClientException("Ошибка от WB API: " + response.getStatusCode());
            }
            if (response.getBody() == null) {
                log.error("Тело ответа от WB API пустое");
                throw new RestClientException("Тело ответа от WB API пустое");
            }
            return response.getBody();
        };

        try {
            return executeWithConnectionRetry(context, oneAttempt);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                log429Metric(STOCKS_SIZES_ENDPOINT, STOCKS_SIZES_OPERATION);
            }
            throwIf401ScopeNotAllowed(e);
            logWbApiError("остатки по размерам на складах WB nmID=" + request.getNmID(), e);
            throw new RestClientException("Ошибка от WB API: " + e.getStatusCode() + " - " + e.getMessage(), e);
        } catch (RestClientException e) {
            logIoErrorOrFull("получении остатков по размерам на складах WB nmID=" + request.getNmID(), e);
            throw e;
        } catch (Exception e) {
            logIoErrorOrFull("получении остатков по размерам на складах WB nmID=" + request.getNmID(), e);
            throw new RestClientException("Ошибка при получении остатков по размерам на складах WB: " + e.getMessage(), e);
        }
    }
}
