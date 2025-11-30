package ru.oparin.solution.service.wb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import ru.oparin.solution.dto.wb.ProductStocksRequest;
import ru.oparin.solution.dto.wb.ProductStocksResponse;

/**
 * Клиент для работы с API остатков товаров.
 * Эндпоинты: получение остатков товаров на складах продавца.
 */
@Service
@Slf4j
public class WbStocksApiClient extends AbstractWbApiClient {

    private static final String STOCKS_ENDPOINT = "/api/v3/stocks/{warehouseId}";

    @Value("${wb.api.marketplace-base-url}")
    private String marketplaceBaseUrl;

    /**
     * Получение остатков товаров на складе продавца.
     *
     * @param apiKey API ключ продавца
     * @param warehouseId ID склада продавца
     * @param request запрос с баркодами
     * @return ответ с остатками товаров
     */
    public ProductStocksResponse getStocks(String apiKey, Long warehouseId, ProductStocksRequest request) {
        HttpHeaders headers = createAuthHeaders(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ProductStocksRequest> entity = new HttpEntity<>(request, headers);
        String url = marketplaceBaseUrl + STOCKS_ENDPOINT.replace("{warehouseId}", String.valueOf(warehouseId));

        try {
            String requestBody = objectMapper.writeValueAsString(request);
            log.info("Запрос остатков товаров: {} {} (warehouseId: {}, {} баркодов, заголовки: Authorization={}, Content-Type={})", 
                    HttpMethod.POST, url, warehouseId, 
                    request.getSkus() != null ? request.getSkus().size() : 0,
                    headers.getFirst("Authorization") != null ? "установлен" : "отсутствует",
                    headers.getContentType());
            log.info("Тело запроса остатков товаров: {}", requestBody);
        } catch (Exception e) {
            log.warn("Не удалось сериализовать тело запроса для логирования: {}", e.getMessage());
        }

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            validateResponse(response);

            ProductStocksResponse stocksResponse = objectMapper.readValue(
                    response.getBody(),
                    ProductStocksResponse.class
            );

            int stocksCount = stocksResponse.getStocks() != null ? stocksResponse.getStocks().size() : 0;
            log.info("Получено остатков товаров: {}", stocksCount);

            return stocksResponse;

        } catch (Exception e) {
            log.error("Ошибка при получении остатков товаров на складе {}: {}", 
                    warehouseId, e.getMessage(), e);
            throw new RestClientException("Ошибка при получении остатков товаров: " + e.getMessage(), e);
        }
    }
}

