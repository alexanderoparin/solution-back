package ru.oparin.solution.service.wb;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import ru.oparin.solution.dto.wb.OrdersResponse;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Клиент для работы с Statistics API Wildberries.
 * Эндпоинты: заказы продавца.
 */
@Service
@Slf4j
public class WbOrdersApiClient extends AbstractWbApiClient {

    private static final String ORDERS_ENDPOINT = "https://statistics-api.wildberries.ru/api/v1/supplier/orders";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Получение заказов за указанную дату.
     * 
     * @param apiKey API ключ продавца
     * @param dateFrom дата начала периода (формат: yyyy-MM-dd)
     * @param flag флаг для фильтрации заказов (1 - только реализованные)
     * @return список заказов
     */
    public List<OrdersResponse.Order> getOrders(String apiKey, LocalDate dateFrom, Integer flag) {
        HttpHeaders headers = createAuthHeaders(apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(ORDERS_ENDPOINT)
                .queryParam("dateFrom", dateFrom.format(DATE_FORMATTER));
        
        if (flag != null) {
            uriBuilder.queryParam("flag", flag);
        }

        String url = uriBuilder.toUriString();
        log.info("Запрос заказов: {} (dateFrom: {}, flag: {})", url, dateFrom, flag);

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

            // Парсим JSON массив напрямую в список заказов
            List<OrdersResponse.Order> orders = objectMapper.readValue(
                    response.getBody(),
                    new TypeReference<List<OrdersResponse.Order>>() {}
            );

            log.info("Получено заказов: {}", orders != null ? orders.size() : 0);
            return orders != null ? orders : List.of();

        } catch (Exception e) {
            log.error("Ошибка при получении заказов: {}", e.getMessage(), e);
            throw new RestClientException("Ошибка при получении заказов: " + e.getMessage(), e);
        }
    }
}
