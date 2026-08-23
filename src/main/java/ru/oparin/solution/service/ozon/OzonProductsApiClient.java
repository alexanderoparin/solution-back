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
import ru.oparin.solution.dto.ozon.*;
import ru.oparin.solution.model.OzonApiEventType;

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

    private static final String HEADER_CLIENT_ID = "Client-Id";
    private static final String HEADER_API_KEY = "Api-Key";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);
    private static final int DEFAULT_PAGE_LIMIT = 100;
    private static final int MAX_BODY_LOG_LENGTH = 2000;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final OzonEndpointRateLimitCoordinator rateLimitCoordinator;

    public OzonProductsApiClient(OzonEndpointRateLimitCoordinator rateLimitCoordinator) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restTemplate = new RestTemplate(requestFactory);
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.rateLimitCoordinator = rateLimitCoordinator;
    }

    /**
     * Постраничный список товаров продавца.
     */
    public OzonProductListResponse listProducts(String clientId, String apiKey, String lastId, int limit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("filter", Map.of("visibility", "ALL"));
        body.put("last_id", lastId != null ? lastId : "");
        body.put("limit", limit > 0 ? limit : DEFAULT_PAGE_LIMIT);
        return postJson(clientId, apiKey, OzonApiEventType.PRODUCT_LIST_PAGE.getDefaultUrl(), body, OzonProductListResponse.class, "product-list");
    }

    /**
     * Детальная информация по product_id (до 1000 за запрос; мы передаём одну страницу).
     */
    public OzonProductInfoListResponse getProductInfoList(String clientId, String apiKey, List<Long> productIds) {
        Map<String, Object> body = Map.of("product_id", productIds);
        return postJson(clientId, apiKey, OzonApiEventType.PRODUCT_INFO_LIST.getDefaultUrl(), body, OzonProductInfoListResponse.class, "product-info-list");
    }

    /**
     * Постраничный список цен товаров.
     */
    public OzonProductInfoPricesResponse listProductPrices(String clientId, String apiKey, String cursor, int limit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cursor", cursor != null ? cursor : "");
        body.put("filter", Map.of("visibility", "ALL"));
        body.put("limit", limit > 0 ? limit : DEFAULT_PAGE_LIMIT);
        return postJson(clientId, apiKey, OzonApiEventType.PRICES_CABINET.getDefaultUrl(), body, OzonProductInfoPricesResponse.class, "product-info-prices");
    }

    /**
     * Постраничные остатки товаров.
     */
    public OzonProductInfoStocksResponse listProductStocks(String clientId, String apiKey, String cursor, int limit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cursor", cursor != null ? cursor : "");
        body.put("filter", Map.of("visibility", "ALL"));
        body.put("limit", limit > 0 ? limit : DEFAULT_PAGE_LIMIT);
        return postJson(clientId, apiKey, OzonApiEventType.STOCKS_CABINET.getDefaultUrl(), body, OzonProductInfoStocksResponse.class, "product-info-stocks");
    }

    /**
     * Аналитика продаж по SKU и дню (базовые метрики без Premium).
     */
    public OzonAnalyticsDataResponse getAnalyticsData(
            String clientId,
            String apiKey,
            String dateFrom,
            String dateTo,
            int limit,
            int offset
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("date_from", dateFrom);
        body.put("date_to", dateTo);
        body.put("metrics", List.of("revenue", "ordered_units"));
        body.put("dimension", List.of("sku", "day"));
        body.put("limit", limit > 0 ? limit : 1000);
        body.put("offset", Math.max(0, offset));
        return postJson(
                clientId,
                apiKey,
                OzonApiEventType.ANALYTICS_DATA_CABINET.getDefaultUrl(),
                body,
                OzonAnalyticsDataResponse.class,
                "analytics-data"
        );
    }

    /**
     * Контент-рейтинг товаров по списку SKU (до 100 за запрос).
     */
    public OzonProductRatingBySkuResponse getProductRatingBySku(String clientId, String apiKey, List<Long> skus) {
        List<String> skuStrings = skus.stream()
                .filter(sku -> sku != null)
                .map(String::valueOf)
                .toList();
        Map<String, Object> body = Map.of("skus", skuStrings);
        return postJson(
                clientId,
                apiKey,
                OzonApiEventType.CONTENT_RATING_CABINET.getDefaultUrl(),
                body,
                OzonProductRatingBySkuResponse.class,
                "product-rating-by-sku"
        );
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
        String endpointKey = OzonEndpointRateLimitCoordinator.endpointKeyFromUrl(url);
        rateLimitCoordinator.beforeRequest(clientId, endpointKey);
        long startedAtMs = System.currentTimeMillis();
        log.info("Ozon API {}: POST {}, Client-Id={}", operation, url, clientId);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            rateLimitCoordinator.afterResponse(clientId, endpointKey, response.getStatusCode().value(), response.getHeaders());
            long elapsedMs = System.currentTimeMillis() - startedAtMs;
            String responseBody = response.getBody();
            log.info("Ozon API {}: ответ HTTP {}, {} ms, тело: {}",
                    operation, response.getStatusCode().value(), elapsedMs, truncateForLog(responseBody));
            if (!response.getStatusCode().is2xxSuccessful() || responseBody == null || responseBody.isBlank()) {
                throw new RestClientException("Неожиданный ответ от Ozon API: " + response.getStatusCode());
            }
            return objectMapper.readValue(responseBody, responseType);
        } catch (HttpClientErrorException e) {
            rateLimitCoordinator.afterResponse(clientId, endpointKey, e.getStatusCode().value(), e.getResponseHeaders());
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
