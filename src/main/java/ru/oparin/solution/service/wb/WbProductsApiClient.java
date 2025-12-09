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

            // Логируем полный сырой ответ для проверки всех полей (включая СПП)
            String responseBody = response.getBody();
            if (responseBody != null) {
                log.info("=== ПОЛНЫЙ СЫРОЙ ОТВЕТ ОТ WB API (длина: {} символов) ===", responseBody.length());
                // Логируем первые 5000 символов для анализа структуры
                int logLength = Math.min(5000, responseBody.length());
                log.info("Первые {} символов ответа:\n{}", logLength, responseBody.substring(0, logLength));
                
                // Если ответ большой, логируем также последние 1000 символов
                if (responseBody.length() > 5000) {
                    int lastStart = Math.max(0, responseBody.length() - 1000);
                    log.info("Последние 1000 символов ответа:\n{}", responseBody.substring(lastStart));
                }
            }

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
            
            // Логируем подробную информацию о первом товаре для проверки всех полей
            if (goodsCount > 0) {
                ProductPricesResponse.Good firstGood = pricesResponse.getData().getListGoods().get(0);
                log.info("=== ДЕТАЛЬНАЯ ИНФОРМАЦИЯ О ПЕРВОМ ТОВАРЕ ===");
                log.info("nmId: {}", firstGood.getNmId());
                log.info("vendorCode: {}", firstGood.getVendorCode());
                log.info("discount: {}", firstGood.getDiscount());
                log.info("clubDiscount: {}", firstGood.getClubDiscount());
                
                if (firstGood.getSizes() != null && !firstGood.getSizes().isEmpty()) {
                    log.info("Количество размеров: {}", firstGood.getSizes().size());
                    for (int i = 0; i < Math.min(3, firstGood.getSizes().size()); i++) {
                        ProductPricesResponse.Size size = firstGood.getSizes().get(i);
                        log.info("--- Размер #{} ---", i + 1);
                        log.info("  sizeId: {}", size.getSizeId());
                        log.info("  techSizeName: {}", size.getTechSizeName());
                        log.info("  price: {}", size.getPrice());
                        log.info("  discountedPrice: {}", size.getDiscountedPrice());
                        log.info("  clubDiscountedPrice: {}", size.getClubDiscountedPrice());
                        log.info("  sppPrice: {} (проверка поля СПП)", size.getSppPrice());
                    }
                } else {
                    log.warn("У первого товара нет размеров!");
                }
            }

            return pricesResponse;

        } catch (Exception e) {
            log.error("Ошибка при получении цен товаров: {}", e.getMessage(), e);
            throw new RestClientException("Ошибка при получении цен товаров: " + e.getMessage(), e);
        }
    }
}

