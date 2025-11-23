package ru.oparin.solution.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Клиент для работы с WB API.
 */
@Service
@Slf4j
public class WbApiClient {

    @Value("${wb.api.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate;

    public WbApiClient() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Валидация WB API ключа через тестовый запрос.
     *
     * @param apiKey API ключ для проверки
     * @return true если ключ валиден, false в противном случае
     */
    public boolean validateApiKey(String apiKey) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", apiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // Используем эндпоинт получения складов для валидации
            String url = baseUrl + "/api/v2/supplier/warehouses";
            
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            // Если получили ответ (даже пустой), ключ валиден
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (RestClientException e) {
            log.error("Ошибка при валидации WB API ключа: {}", e.getMessage());
            return false;
        }
    }
}

