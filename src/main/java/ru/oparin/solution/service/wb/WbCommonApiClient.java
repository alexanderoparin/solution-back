package ru.oparin.solution.service.wb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import ru.oparin.solution.dto.wb.SellerInfoResponse;

/**
 * Клиент для работы с Common API Wildberries.
 * Эндпоинты: информация о продавце.
 * Категория WB API: Тарифы, новости, информация о продавце.
 */
@Service
@Slf4j
public class WbCommonApiClient extends AbstractWbApiClient {

    @Override
    protected WbApiCategory getApiCategory() {
        return WbApiCategory.COMMON;
    }

    private static final String SELLER_INFO_URL = "https://common-api.wildberries.ru/api/v1/seller-info";

    /**
     * Получение информации о продавце.
     */
    public SellerInfoResponse getSellerInfo(String apiKey) {
        HttpHeaders headers = createAuthHeaders(apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        logWbApiCall(SELLER_INFO_URL, "информация о продавце");

        try {
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
        } catch (HttpClientErrorException e) {
            throwIf401ScopeNotAllowed(e);
            logWbApiError("информация о продавце WB", e);
            throw new RestClientException("Ошибка при получении информации о продавце: " + e.getMessage(), e);
        }
    }
}

