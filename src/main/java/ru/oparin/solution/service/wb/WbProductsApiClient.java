package ru.oparin.solution.service.wb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import ru.oparin.solution.dto.wb.ProductPricesRequest;
import ru.oparin.solution.dto.wb.ProductPricesResponse;

/**
 * Клиент для работы с Products API Wildberries.
 * Эндпоинты: цены и скидки товаров.
 */
@Service
@Slf4j
public class WbProductsApiClient extends AbstractWbApiClient {

    private static final String PRODUCT_PRICES_ENDPOINT = "/api/v2/list/goods/filter";

    @Value("${wb.api.discounts-prices-base-url}")
    private String discountsPricesBaseUrl;

    /**
     * Получение цен и скидок товаров по артикулам.
     */
    public ProductPricesResponse getProductPrices(String apiKey, ProductPricesRequest request) {
        HttpHeaders headers = createAuthHeaders(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ProductPricesRequest> entity = new HttpEntity<>(request, headers);
        String url = discountsPricesBaseUrl + PRODUCT_PRICES_ENDPOINT;

        log.info("Запрос цен товаров: {} ({} товаров)", url, 
                request.getNmList() != null ? request.getNmList().size() : 0);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            validateResponse(response);

            ProductPricesResponse pricesResponse = objectMapper.readValue(
                    response.getBody(), 
                    ProductPricesResponse.class
            );

            if (pricesResponse.getError() != null && pricesResponse.getError()) {
                log.error("Ошибка от WB API при получении цен: {}", pricesResponse.getErrorText());
                throw new RestClientException("Ошибка от WB API: " + pricesResponse.getErrorText());
            }

            int goodsCount = pricesResponse.getData() != null && pricesResponse.getData().getListGoods() != null
                    ? pricesResponse.getData().getListGoods().size()
                    : 0;
            log.info("Получено товаров с ценами: {}", goodsCount);

            return pricesResponse;

        } catch (HttpClientErrorException e) {
            logWbApiError("получение цен товаров", e);
            throw new RestClientException("Ошибка при получении цен товаров: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Ошибка при получении цен товаров: {}", e.getMessage(), e);
            throw new RestClientException("Ошибка при получении цен товаров: " + e.getMessage(), e);
        }
    }
}

