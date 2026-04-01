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

    @Value("${wb.api.analytics-base-url}")
    private String analyticsBaseUrl;
    @Value("${wb.stocks.max-retries-429}")
    private int maxRetries429;
    @Value("${wb.stocks.retry-delay-ms-429}")
    private long retryDelayMs429;

    /**
     * Получение остатков товаров по размерам на складах WB.
     * При 429 — до 5 повторов с задержкой 20 с. Ретраи при 504/таймауте/соединении — в базовом клиенте.
     */
    public WbStocksSizesResponse getWbStocksBySizes(String apiKey, WbStocksSizesRequest request) {
        return getWbStocksBySizesWithRetry(apiKey, request, maxRetries429);
    }

    private WbStocksSizesResponse getWbStocksBySizesWithRetry(String apiKey, WbStocksSizesRequest request, int maxRetries) {
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

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return executeWithConnectionRetry(context, oneAttempt);
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429 && attempt < maxRetries) {
                    log429Metric();
                    log.warn("Получен 429 Too Many Requests для nmID={} (попытка {}/{}). Ожидание {} мс перед повторной попыткой...",
                            request.getNmID(), attempt, maxRetries, retryDelayMs429);
                    sleep(retryDelayMs429);
                    continue;
                }
                throwIf401ScopeNotAllowed(e);
                logWbApiError("остатки по размерам на складах WB nmID=" + request.getNmID(), e);
                throw new RestClientException("Ошибка от WB API: " + e.getStatusCode() + " - " + e.getMessage(), e);
            } catch (RestClientException e) {
                if (e.getMessage() != null && e.getMessage().contains("429") && attempt < maxRetries) {
                    log429Metric();
                    log.warn("Ошибка 429 для nmID={} при попытке {}/{}. Повтор через {} мс...",
                            request.getNmID(), attempt, maxRetries, retryDelayMs429);
                    sleep(retryDelayMs429);
                    continue;
                }
                if (attempt == maxRetries) {
                    logIoErrorOrFull("получении остатков по размерам на складах WB nmID=" + request.getNmID() + " после " + maxRetries + " попыток", e);
                }
                throw e;
            } catch (Exception e) {
                if (attempt == maxRetries) {
                    logIoErrorOrFull("получении остатков по размерам на складах WB nmID=" + request.getNmID(), e);
                } else {
                    log.warn("Ошибка при получении остатков по размерам на складах WB nmID={} (попытка {}/{}): {}",
                            request.getNmID(), attempt, maxRetries, e.getMessage());
                }
                if (attempt == maxRetries) {
                    throw new RestClientException("Ошибка при получении остатков по размерам на складах WB: " + e.getMessage(), e);
                }
                sleep(CONNECTION_RETRY_DELAY_MS);
            }
        }
        throw new RestClientException("Не удалось получить остатки по размерам после " + maxRetries + " попыток");
    }
}
