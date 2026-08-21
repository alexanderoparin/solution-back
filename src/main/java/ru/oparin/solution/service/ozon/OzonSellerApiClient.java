package ru.oparin.solution.service.ozon;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.oparin.solution.dto.ozon.OzonSellerInfoResponse;

/**
 * Клиент Seller API Ozon для проверки учётных данных и базовых запросов.
 */
@Service
@Slf4j
public class OzonSellerApiClient {

    private static final String SELLER_INFO_URL = "https://api-seller.ozon.ru/v1/seller/info";
    private static final String HEADER_CLIENT_ID = "Client-Id";
    private static final String HEADER_API_KEY = "Api-Key";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int MAX_BODY_LOG_LENGTH = 500;

    private final RestTemplate restTemplate;

    public OzonSellerApiClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MS);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    /**
     * Проверяет Client-Id + Api-Key и возвращает информацию о продавце.
     *
     * @param clientId Client-Id из кабинета продавца Ozon
     * @param apiKey   Api-Key из кабинета продавца Ozon
     * @return данные продавца при успешной авторизации
     */
    public OzonSellerInfoResponse getSellerInfo(String clientId, String apiKey) {
        HttpHeaders headers = createAuthHeaders(clientId, apiKey);
        HttpEntity<String> entity = new HttpEntity<>("{}", headers);

        log.info("Ozon API seller-info: POST {}, Client-Id={}", SELLER_INFO_URL, clientId);

        try {
            ResponseEntity<OzonSellerInfoResponse> response = restTemplate.exchange(
                    SELLER_INFO_URL,
                    HttpMethod.POST,
                    entity,
                    OzonSellerInfoResponse.class
            );
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RestClientException("Неожиданный ответ от Ozon API: " + response.getStatusCode());
            }
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.warn("Ozon API seller-info: HTTP {} {}, тело: {}",
                    e.getStatusCode().value(),
                    e.getStatusText(),
                    truncateForLog(e.getResponseBodyAsString()));
            throw e;
        } catch (RestClientException e) {
            log.warn("Ozon API seller-info: ошибка запроса: {}", e.getMessage());
            throw e;
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
            return "";
        }
        if (body.length() <= MAX_BODY_LOG_LENGTH) {
            return body;
        }
        return body.substring(0, MAX_BODY_LOG_LENGTH) + "...";
    }
}
