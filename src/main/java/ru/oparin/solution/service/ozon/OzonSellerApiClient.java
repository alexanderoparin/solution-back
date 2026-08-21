package ru.oparin.solution.service.ozon;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.oparin.solution.dto.ozon.OzonSellerInfoResponse;

import java.time.Duration;

/**
 * Клиент Seller API Ozon для проверки учётных данных и базовых запросов.
 */
@Service
@Slf4j
public class OzonSellerApiClient {

    private static final String SELLER_INFO_URL = "https://api-seller.ozon.ru/v1/seller/info";
    private static final String HEADER_CLIENT_ID = "Client-Id";
    private static final String HEADER_API_KEY = "Api-Key";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_BODY_LOG_LENGTH = 2000;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OzonSellerApiClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restTemplate = new RestTemplate(requestFactory);
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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
        long startedAtMs = System.currentTimeMillis();

        log.info("Ozon API seller-info: запрос POST {}, Client-Id={}", SELLER_INFO_URL, clientId);

        try {
            // Строка — чтобы всегда залогировать сырое тело (как у WB seller-info).
            ResponseEntity<String> response = restTemplate.exchange(
                    SELLER_INFO_URL,
                    HttpMethod.POST,
                    entity,
                    String.class
            );
            long elapsedMs = System.currentTimeMillis() - startedAtMs;
            String body = response.getBody();
            log.info("Ozon API seller-info: ответ HTTP {}, {} ms, тело: {}",
                    response.getStatusCode().value(),
                    elapsedMs,
                    truncateForLog(body));

            if (!response.getStatusCode().is2xxSuccessful() || body == null || body.isBlank()) {
                throw new RestClientException("Неожиданный ответ от Ozon API: " + response.getStatusCode());
            }
            return objectMapper.readValue(body, OzonSellerInfoResponse.class);
        } catch (HttpClientErrorException e) {
            long elapsedMs = System.currentTimeMillis() - startedAtMs;
            log.warn("Ozon API seller-info: ответ HTTP {} {}, {} ms, тело: {}",
                    e.getStatusCode().value(),
                    e.getStatusText(),
                    elapsedMs,
                    truncateForLog(e.getResponseBodyAsString()));
            throw e;
        } catch (RestClientException e) {
            long elapsedMs = System.currentTimeMillis() - startedAtMs;
            log.warn("Ozon API seller-info: ошибка запроса через {} ms: {}", elapsedMs, e.getMessage());
            throw e;
        } catch (Exception e) {
            long elapsedMs = System.currentTimeMillis() - startedAtMs;
            log.warn("Ozon API seller-info: сбой через {} ms: {}", elapsedMs, e.getMessage());
            throw new RestClientException("Ошибка при запросе seller-info Ozon: " + e.getMessage(), e);
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
