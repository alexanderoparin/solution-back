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
import ru.oparin.solution.dto.wb.WbWarehouseResponse;
import ru.oparin.solution.model.WbApiEventType;

import java.util.List;

/**
 * Клиент для работы с API складов WB (supplies-api).
 * Эндпоинты: список складов WB.
 * Категория WB API: Поставки (или Маркетплейс — склады продавца по доке).
 */
@Service
@Slf4j
public class WbWarehousesApiClient extends AbstractWbApiClient {

    @Override
    protected WbApiCategory getApiCategory() {
        return WbApiCategory.MARKETPLACE;
    }

    /**
     * Получение списка всех складов WB.
     * При таймауте или ошибке соединения выполняются ретраи.
     */
    public List<WbWarehouseResponse> getWbOffices(String apiKey) {
        return executeWithConnectionRetry("список складов WB", () -> getWbOfficesOnce(apiKey));
    }

    private List<WbWarehouseResponse> getWbOfficesOnce(String apiKey) {
        HttpHeaders headers = createAuthHeaders(apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        String url = WbApiEventType.WAREHOUSES_SYNC_CABINET.getDefaultUrl();

        logWbApiCall(url, "список складов WB");

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
                    new TypeReference<>() {
                    }
            );

            log.info("Получено складов WB: {}", warehouses != null ? warehouses.size() : 0);

            return warehouses != null ? warehouses : List.of();

        } catch (HttpClientErrorException e) {
            throwIf401ScopeNotAllowed(e);
            logWbApiError("список складов WB", e);
            throw new RestClientException("Ошибка при получении списка складов WB: " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw e;
        } catch (Exception e) {
            logIoErrorOrFull("получении списка складов WB", e);
            throw new RestClientException("Ошибка при получении списка складов WB: " + e.getMessage(), e);
        }
    }

}

