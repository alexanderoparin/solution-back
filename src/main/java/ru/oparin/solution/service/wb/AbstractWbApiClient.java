package ru.oparin.solution.service.wb;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Абстрактный базовый класс для клиентов WB API.
 * Содержит общие методы для работы с HTTP запросами.
 */
@Slf4j
public abstract class AbstractWbApiClient {

    protected static final String BEARER_PREFIX = "Bearer ";

    protected final RestTemplate restTemplate;
    protected final ObjectMapper objectMapper;

    protected AbstractWbApiClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false
        );
    }

    /**
     * Создает заголовки с авторизацией (без префикса Bearer).
     */
    protected HttpHeaders createAuthHeaders(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", apiKey);
        return headers;
    }

    /**
     * Создает заголовки с авторизацией и префиксом Bearer.
     */
    protected HttpHeaders createAuthHeadersWithBearer(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        String authHeader = apiKey.startsWith(BEARER_PREFIX) ? apiKey : BEARER_PREFIX + apiKey;
        headers.set("Authorization", authHeader);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /**
     * Выполняет POST запрос и возвращает строковый ответ.
     */
    protected <T> ResponseEntity<String> executePostRequest(String url, HttpEntity<T> entity) {
        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
        );
        
        validateResponse(response);
        return response;
    }

    /**
     * Валидирует ответ от WB API.
     */
    protected void validateResponse(ResponseEntity<String> response) {
        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("Ошибка от WB API: статус={}", response.getStatusCode());
            throw new RestClientException("Ошибка от WB API: " + response.getStatusCode());
        }
        
        if (response.getBody() == null || response.getBody().isEmpty()) {
            log.error("Тело ответа от WB API пустое");
            throw new RestClientException("Тело ответа от WB API пустое");
        }
    }

    /**
     * Выполняет запрос с retry для ошибок 429.
     */
    protected <T> ResponseEntity<String> executeWithRetry(
            String url,
            String apiKey,
            T requestBody,
            int maxRetries,
            long retryDelayMs
    ) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpHeaders headers = createAuthHeaders(apiKey);
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<T> entity = new HttpEntity<>(requestBody, headers);
                
                ResponseEntity<String> response = restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        entity,
                        String.class
                );
                
                if (response.getStatusCode().value() == 429 && attempt < maxRetries) {
                    log.warn("Получен 429 Too Many Requests (попытка {}/{}). Ожидание {} мс...", 
                            attempt, maxRetries, retryDelayMs);
                    
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RestClientException("Прервано ожидание перед повторной попыткой", ie);
                    }
                    continue;
                }
                
                if (response.getStatusCode().value() == 429) {
                    throw new RestClientException("429 Too Many Requests: " + response.getBody());
                }
                
                validateResponse(response);
                return response;
                
            } catch (RestClientException e) {
                if (is429Error(e) && attempt < maxRetries) {
                    continue;
                }
                throw e;
            }
        }
        
        throw new RestClientException("Не удалось выполнить запрос после " + maxRetries + " попыток");
    }

    /**
     * Проверяет, является ли ошибка ошибкой 429.
     */
    private boolean is429Error(RestClientException e) {
        return e.getMessage() != null && e.getMessage().contains("429");
    }
}

