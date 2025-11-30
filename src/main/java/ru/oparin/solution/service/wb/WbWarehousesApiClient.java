package ru.oparin.solution.service.wb;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import ru.oparin.solution.dto.wb.WbWarehouseResponse;

import java.util.List;

/**
 * Клиент для работы с API складов WB.
 * Эндпоинты: список складов WB.
 */
@Service
@Slf4j
public class WbWarehousesApiClient extends AbstractWbApiClient {

    private static final String WB_OFFICES_ENDPOINT = "/api/v3/offices";

    @Value("${wb.api.marketplace-base-url}")
    private String marketplaceBaseUrl;

    /**
     * Получение списка всех складов WB.
     */
    public List<WbWarehouseResponse> getWbOffices(String apiKey) {
        HttpHeaders headers = createAuthHeaders(apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        String url = marketplaceBaseUrl + WB_OFFICES_ENDPOINT;

        log.info("Запрос списка складов WB: {}", url);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            validateResponse(response);

            List<WbWarehouseResponse> warehouses = objectMapper.readValue(
                    response.getBody(),
                    new TypeReference<List<WbWarehouseResponse>>() {}
            );

            log.info("Получено складов WB: {}", warehouses != null ? warehouses.size() : 0);

            return warehouses != null ? warehouses : List.of();

        } catch (Exception e) {
            log.error("Ошибка при получении списка складов WB: {}", e.getMessage(), e);
            throw new RestClientException("Ошибка при получении списка складов WB: " + e.getMessage(), e);
        }
    }
}

