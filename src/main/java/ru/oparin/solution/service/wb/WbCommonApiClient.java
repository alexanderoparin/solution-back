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
import ru.oparin.solution.model.WbApiEventType;

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

    private static final String SELLER_INFO_URL = WbApiEventType.COMMON_SELLER_INFO.getDefaultUrl();
    /** Имя операции для метрик 429 (см. {@link AbstractWbApiClient#log429Metric}). */
    private static final String SELLER_INFO_OPERATION = "seller-info";
    private static final int MAX_RESPONSE_BODY_LOG_LENGTH = 2000;

    /**
     * Получение информации о продавце.
     * При таймауте или ошибке соединения выполняются ретраи.
     */
    public SellerInfoResponse getSellerInfo(String apiKey) {
        return executeWithConnectionRetry("информация о продавце", () -> getSellerInfoOnce(apiKey));
    }

    private SellerInfoResponse getSellerInfoOnce(String apiKey) {
        HttpHeaders headers = createAuthHeaders(apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        logWbApiCall(SELLER_INFO_URL, "информация о продавце");
        log.info("WB API seller-info: запрос GET {}, Authorization={}", SELLER_INFO_URL, maskTokenForLog(apiKey));

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
            logSellerInfoResponse(response.getStatusCode().value(), response.getBody());
            return response.getBody();
        } catch (HttpClientErrorException e) {
            throwIf401ScopeNotAllowed(e);
            log.warn("WB API seller-info: ответ HTTP {} {}, тело: {}",
                    e.getStatusCode().value(),
                    e.getStatusText(),
                    truncateForLog(e.getResponseBodyAsString()));
            logWbApiError("информация о продавце WB", e, extractEndpointPath(SELLER_INFO_URL), SELLER_INFO_OPERATION);
            throw e;
        } catch (RestClientException e) {
            throw e;
        } catch (Exception e) {
            logIoErrorOrFull("получении информации о продавце", e);
            throw new RestClientException("Ошибка при получении информации о продавце: " + e.getMessage(), e);
        }
    }

    private void logSellerInfoResponse(int httpStatus, SellerInfoResponse body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            log.info("WB API seller-info: ответ HTTP {}, тело: {}", httpStatus, truncateForLog(json));
        } catch (Exception e) {
            log.warn("WB API seller-info: не удалось сериализовать ответ в JSON для лога: {}", e.getMessage());
            log.info("WB API seller-info: ответ HTTP {}, name={}, sid={}, tradeMark={}",
                    httpStatus, body.getName(), body.getSid(), body.getTradeMark());
        }
    }

    private static String maskTokenForLog(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "(пусто)";
        }
        String t = apiKey.trim();
        if (t.length() <= 12) {
            return "***";
        }
        return t.substring(0, 6) + "…" + t.substring(t.length() - 4);
    }

    private static String truncateForLog(String body) {
        if (body == null) {
            return "";
        }
        if (body.length() <= MAX_RESPONSE_BODY_LOG_LENGTH) {
            return body;
        }
        return body.substring(0, MAX_RESPONSE_BODY_LOG_LENGTH) + "…";
    }
}

