package ru.oparin.solution.service.ozon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.oparin.solution.dto.ozon.OzonProductInfoListResponse;
import ru.oparin.solution.dto.ozon.OzonProductInfoPricesResponse;
import ru.oparin.solution.dto.ozon.OzonProductInfoStocksResponse;
import ru.oparin.solution.dto.ozon.OzonProductListResponse;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Клиент Ozon Seller API для каталога товаров.
 */
@Service
@Slf4j
public class OzonProductsApiClient {

    private static final String PRODUCT_LIST_URL = "https://api-seller.ozon.ru/v3/product/list";
    private static final String PRODUCT_INFO_LIST_URL = "https://api-seller.ozon.ru/v3/product/info/list";
    private static final String PRODUCT_INFO_PRICES_URL = "https://api-seller.ozon.ru/v5/product/info/prices";
    private static final String PRODUCT_INFO_STOCKS_URL = "https://api-seller.ozon.ru/v4/product/info/stocks";
    private static final String HEADER_CLIENT_ID = "Client-Id";
    private static final String HEADER_API_KEY = "Api-Key";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);
    private static final int DEFAULT_PAGE_LIMIT = 100;
    private static final int MAX_BODY_LOG_LENGTH = 2000;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OzonProductsApiClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restTemplate = new RestTemplate(requestFactory);
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Постраничный список товаров продавца.
     */
    public OzonProductListResponse listProducts(String clientId, String apiKey, String lastId, int limit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("filter", Map.of("visibility", "ALL"));
        body.put("last_id", lastId != null ? lastId : "");
        body.put("limit", limit > 0 ? limit : DEFAULT_PAGE_LIMIT);
        return postJson(clientId, apiKey, PRODUCT_LIST_URL, body, OzonProductListResponse.class, "product-list");
    }

    /**
     * Детальная информация по product_id (до 1000 за запрос; мы передаём одну страницу).
     */
    public OzonProductInfoListResponse getProductInfoList(String clientId, String apiKey, List<Long> productIds) {
        Map<String, Object> body = Map.of("product_id", productIds);
        return postJson(clientId, apiKey, PRODUCT_INFO_LIST_URL, body, OzonProductInfoListResponse.class, "product-info-list");
    }

    /**
     * Постраничный список цен товаров.
     */
    public OzonProductInfoPricesResponse listProductPrices(String clientId, String apiKey, String cursor, int limit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cursor", cursor != null ? cursor : "");
        body.put("filter", Map.of("visibility", "ALL"));
        body.put("limit", limit > 0 ? limit : DEFAULT_PAGE_LIMIT);
        return postJson(clientId, apiKey, PRODUCT_INFO_PRICES_URL, body, OzonProductInfoPricesResponse.class, "product-info-prices");
    }

    /**
     * Постраничные остатки товаров.
     */
    public OzonProductInfoStocksResponse listProductStocks(String clientId, String apiKey, String cursor, int limit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cursor", cursor != null ? cursor : "");
        body.put("filter", Map.of("visibility", "ALL"));
        body.put("limit", limit > 0 ? limit : DEFAULT_PAGE_LIMIT);
        return postJson(clientId, apiKey, PRODUCT_INFO_STOCKS_URL, body, OzonProductInfoStocksResponse.class, "product-info-stocks");
    }

    private <T> T postJson(String clientId, String apiKey, String url, Object body, Class<T> responseType, String operation) {
        HttpHeaders headers = createAuthHeaders(clientId, apiKey);
        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new RestClientException("Не удалось сериализовать тело запроса Ozon: " + e.getMessage(), e);
        }
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        long startedAtMs = System.currentTimeMillis();
        log.info("Ozon API {}: POST {}, Client-Id={}", operation, url, clientId);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            long elapsedMs = System.currentTimeMillis() - startedAtMs;
            String responseBody = response.getBody();
            log.info("Ozon API {}: ответ HTTP {}, {} ms, тело: {}",
                    operation, response.getStatusCode().value(), elapsedMs, truncateForLog(responseBody));
            if (!response.getStatusCode().is2xxSuccessful() || responseBody == null || responseBody.isBlank()) {
                throw new RestClientException("Неожиданный ответ от Ozon API: " + response.getStatusCode());
            }
            return objectMapper.readValue(responseBody, responseType);
        } catch (HttpClientErrorException e) {
            long elapsedMs = System.currentTimeMillis() - startedAtMs;
            log.warn("Ozon API {}: ответ HTTP {} {}, {} ms, тело: {}",
                    operation, e.getStatusCode().value(), e.getStatusText(), elapsedMs,
                    truncateForLog(e.getResponseBodyAsString()));
            throw e;
        } catch (RestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new RestClientException("Ошибка Ozon API " + operation + ": " + e.getMessage(), e);
        }
    }

    private static HttpHeaders createAuthHeaders(String clientId, String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HEADER_CLIENT_ID, clientId);
        headers.set(HEADER_API_KEY, apiKey);
        return headers;
    }

    private static String truncateForLog(String body) {
        if (body == null || body.isBlank()) {
            return "(пусто)";
        }
        if (body.length() <= MAX_BODY_LOG_LENGTH) {
            return body;
        }
        return body.substring(0, MAX_BODY_LOG_LENGTH) + "...";
    }
}
