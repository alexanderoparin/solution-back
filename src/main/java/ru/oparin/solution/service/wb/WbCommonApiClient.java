package ru.oparin.solution.service.wb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import ru.oparin.solution.dto.wb.SellerInfoResponse;

/**
 * Клиент для работы с Common API Wildberries.
 * Эндпоинты: валидация ключа, информация о продавце.
 */
@Service
@Slf4j
public class WbCommonApiClient extends AbstractWbApiClient {

    private static final String SELLER_INFO_URL = "https://common-api.wildberries.ru/api/v1/seller-info";
    private static final String VALIDATION_ENDPOINT = "/api/v2/supplier/warehouses";

    @Value("${wb.api.base-url}")
    private String baseUrl;

    /**
     * Валидация WB API ключа через тестовый запрос.
     */
    public boolean validateApiKey(String apiKey) {
        try {
            HttpHeaders headers = createAuthHeaders(apiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            String url = baseUrl + VALIDATION_ENDPOINT;
            
            log.info("Запрос валидации API ключа: {}", url);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            return response.getStatusCode().is2xxSuccessful();
            
        } catch (RestClientException e) {
            log.error("Ошибка при валидации WB API ключа: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Получение информации о продавце.
     */
    public SellerInfoResponse getSellerInfo(String apiKey) {
        HttpHeaders headers = createAuthHeaders(apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        log.info("Запрос информации о продавце: {}", SELLER_INFO_URL);
        
        ResponseEntity<SellerInfoResponse> response = restTemplate.exchange(
                SELLER_INFO_URL,
                HttpMethod.GET,
                entity,
                SellerInfoResponse.class
        );
        
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RestClientException("Неожиданный ответ от WB API: " + response.getStatusCode());
        }
        
        return response.getBody();
    }
}

