package ru.oparin.solution.service.wb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import ru.oparin.solution.dto.wb.WbStocksSizesRequest;
import ru.oparin.solution.dto.wb.WbStocksSizesResponse;

/**
 * Клиент для работы с API остатков товаров.
 * Эндпоинты: получение остатков товаров по размерам на складах WB.
 */
@Service
@Slf4j
public class WbStocksApiClient extends AbstractWbApiClient {

    private static final String STOCKS_SIZES_ENDPOINT = "/api/v2/stocks-report/products/sizes";

    @Value("${wb.api.analytics-base-url}")
    private String analyticsBaseUrl;

    private static final int MAX_RETRIES_429 = 5;
    private static final long RETRY_DELAY_MS_429 = 20000; // 20 секунд

    /**
     * Получение остатков товаров по размерам на складах WB.
     * При ошибке 429 (Too Many Requests) выполняет повторные попытки с задержкой.
     *
     * @param apiKey API ключ продавца (токен для категории "Аналитика")
     * @param request запрос с артикулом и параметрами
     * @return ответ с остатками товаров по размерам на складах WB
     */
    public WbStocksSizesResponse getWbStocksBySizes(String apiKey, WbStocksSizesRequest request) {
        return getWbStocksBySizesWithRetry(apiKey, request, MAX_RETRIES_429);
    }

    /**
     * Получение остатков товаров по размерам на складах WB с повторными попытками при 429.
     *
     * @param apiKey API ключ продавца
     * @param request запрос с артикулом и параметрами
     * @param maxRetries максимальное количество попыток
     * @return ответ с остатками товаров по размерам на складах WB
     */
    private WbStocksSizesResponse getWbStocksBySizesWithRetry(String apiKey, WbStocksSizesRequest request, int maxRetries) {
        HttpHeaders headers = createAuthHeaders(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<WbStocksSizesRequest> entity = new HttpEntity<>(request, headers);
        String url = analyticsBaseUrl + STOCKS_SIZES_ENDPOINT;

        log.debug("Запрос остатков по размерам на складах WB для nmID: {}", request.getNmID());

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
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

            } catch (HttpClientErrorException e) {
                // Обрабатываем 429 ошибку
                if (e.getStatusCode().value() == 429) {
                    if (attempt < maxRetries) {
                        log.warn("Получен 429 Too Many Requests (попытка {}/{}). Ожидание {} мс перед повторной попыткой...", 
                                attempt, maxRetries, RETRY_DELAY_MS_429);
                        try {
                            Thread.sleep(RETRY_DELAY_MS_429);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RestClientException("Прервано ожидание перед повторной попыткой", ie);
                        }
                        continue; // Повторяем попытку
                    } else {
                        log.error("Получен 429 Too Many Requests после {} попыток", maxRetries);
                        throw new RestClientException("429 Too Many Requests после " + maxRetries + " попыток");
                    }
                }
                // Для других HTTP ошибок клиента пробрасываем дальше
                log.error("Ошибка от WB API: статус={}, сообщение={}", e.getStatusCode(), e.getMessage());
                throw new RestClientException("Ошибка от WB API: " + e.getStatusCode() + " - " + e.getMessage(), e);
            } catch (RestClientException e) {
                // Если это 429 и есть еще попытки, продолжаем цикл
                if (e.getMessage() != null && e.getMessage().contains("429") && attempt < maxRetries) {
                    log.warn("Ошибка 429 при попытке {}/{}. Повтор через {} мс...", 
                            attempt, maxRetries, RETRY_DELAY_MS_429);
                    try {
                        Thread.sleep(RETRY_DELAY_MS_429);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RestClientException("Прервано ожидание перед повторной попыткой", ie);
                    }
                    continue;
                }
                
                // Если это последняя попытка или другая ошибка, пробрасываем исключение
                if (attempt == maxRetries) {
                    log.error("Ошибка при получении остатков по размерам на складах WB после {} попыток: {}", 
                            maxRetries, e.getMessage(), e);
                }
                throw e;
            } catch (Exception e) {
                log.error("Ошибка при получении остатков по размерам на складах WB (попытка {}/{}): {}", 
                        attempt, maxRetries, e.getMessage(), e);
                if (attempt == maxRetries) {
                    throw new RestClientException("Ошибка при получении остатков по размерам на складах WB: " + e.getMessage(), e);
                }
                // Для других ошибок тоже делаем retry
                try {
                    Thread.sleep(RETRY_DELAY_MS_429);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RestClientException("Прервано ожидание перед повторной попыткой", ie);
                }
            }
        }

        throw new RestClientException("Не удалось получить остатки по размерам после " + maxRetries + " попыток");
    }
}
