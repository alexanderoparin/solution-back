package ru.oparin.solution.service.wb;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import ru.oparin.solution.dto.wb.OrdersResponse;
import ru.oparin.solution.model.WbApiEventType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Клиент для работы с Statistics API Wildberries.
 * Эндпоинты: заказы продавца.
 * Категория WB API: Статистика.
 */
@Service
@Slf4j
public class WbOrdersApiClient extends AbstractWbApiClient {

    @Override
    protected WbApiCategory getApiCategory() {
        return WbApiCategory.STATISTICS;
    }

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Получение заказов за указанную дату.
     * При таймауте или ошибке соединения выполняются ретраи.
     *
     * @param apiKey API ключ продавца
     * @param dateFrom дата начала периода (формат: yyyy-MM-dd)
     * @param flag флаг для фильтрации заказов (1 - только реализованные)
     * @return список заказов
     */
    public List<OrdersResponse.Order> getOrders(String apiKey, LocalDate dateFrom, Integer flag) {
        return executeWithConnectionRetry("заказы продавца (СПП)", () -> getOrdersOnce(apiKey, dateFrom, flag));
    }

    private List<OrdersResponse.Order> getOrdersOnce(String apiKey, LocalDate dateFrom, Integer flag) {
        HttpHeaders headers = createAuthHeaders(apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(
                        WbApiEventType.STATISTICS_SUPPLIER_ORDERS.getDefaultUrl())
                .queryParam("dateFrom", dateFrom.format(DATE_FORMATTER));

        if (flag != null) {
            uriBuilder.queryParam("flag", flag);
        }

        String url = uriBuilder.toUriString();
        logWbApiCall(url, "заказы продавца (СПП)");

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RestClientException("Неожиданный ответ от WB API: " + response.getStatusCode());
            }

            List<OrdersResponse.Order> orders = objectMapper.readValue(
                    response.getBody(),
                    new TypeReference<List<OrdersResponse.Order>>() {}
            );

            log.info("Получено заказов: {}", orders != null ? orders.size() : 0);
            return orders != null ? orders : List.of();

        } catch (HttpClientErrorException e) {
            throwIf401ScopeNotAllowed(e);
            logWbApiError("получение заказов WB", e);
            throw new RestClientException("Ошибка при получении заказов: " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw e;
        } catch (Exception e) {
            logIoErrorOrFull("получении заказов", e);
            throw new RestClientException("Ошибка при получении заказов: " + e.getMessage(), e);
        }
    }
}
