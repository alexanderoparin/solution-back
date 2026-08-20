package ru.oparin.solution.service.wb;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import ru.oparin.solution.dto.wb.WbFbsStocksRequest;
import ru.oparin.solution.dto.wb.WbFbsStocksResponse;
import ru.oparin.solution.dto.wb.WbSellerWarehouseResponse;
import ru.oparin.solution.model.WbApiEventType;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Клиент Marketplace API: склады продавца и остатки FBS.
 * Категория WB API: Маркетплейс.
 */
@Service
@Slf4j
public class WbFbsApiClient extends AbstractWbApiClient {

    private static final String WAREHOUSES_OPERATION = "склады продавца";
    private static final String STOCKS_OPERATION = "остатки FBS";

    @Override
    protected WbApiCategory getApiCategory() {
        return WbApiCategory.MARKETPLACE;
    }

    /**
     * Список складов продавца (GET /api/v3/warehouses).
     *
     * @param apiKey токен кабинета с категорией «Маркетплейс»
     * @return склады продавца; пустой список, если тело пустое
     */
    public List<WbSellerWarehouseResponse> getWbSellerWarehouses(String apiKey) {
        return executeWithConnectionRetry("список складов продавца", () -> getWbSellerWarehousesOnce(apiKey));
    }

    /**
     * Остатки на складе продавца по списку chrtId (POST /api/v3/stocks/{warehouseId}).
     * Не более 1000 chrtId за запрос. Отсутствующие в WB размеры в ответ не попадают.
     *
     * @param apiKey      токен кабинета с категорией «Маркетплейс»
     * @param warehouseId ID склада продавца
     * @param chrtIds     ID размеров (chrtId)
     * @return ответ WB с массивом stocks
     */
    public WbFbsStocksResponse getFbsStocks(String apiKey, Long warehouseId, List<Long> chrtIds) {
        String context = "остатки FBS warehouseId=" + warehouseId;
        Callable<WbFbsStocksResponse> oneAttempt = () -> getFbsStocksOnce(apiKey, warehouseId, chrtIds);
        try {
            return executeWithConnectionRetry(context, oneAttempt);
        } catch (HttpClientErrorException e) {
            throwIf401ScopeNotAllowed(e);
            logWbApiError(
                    context,
                    e,
                    WbApiEventType.FBS_STOCKS_CABINET.getUri(),
                    STOCKS_OPERATION
            );
            throw e;
        } catch (RestClientException e) {
            logIoErrorOrFull(context, e);
            throw e;
        } catch (Exception e) {
            logIoErrorOrFull(context, e);
            throw new RestClientException("Ошибка при получении остатков FBS: " + e.getMessage(), e);
        }
    }

    private List<WbSellerWarehouseResponse> getWbSellerWarehousesOnce(String apiKey) {
        HttpHeaders headers = createAuthHeaders(apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        String url = WbApiEventType.FBS_WAREHOUSES_SYNC_CABINET.getDefaultUrl();
        logWbApiCall(url, "список складов продавца");
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            validateResponse(response);
            List<WbSellerWarehouseResponse> warehouses = objectMapper.readValue(
                    response.getBody(),
                    new TypeReference<>() {
                    }
            );
            log.info("Получено складов продавца: {}", warehouses != null ? warehouses.size() : 0);
            return warehouses != null ? warehouses : List.of();
        } catch (HttpClientErrorException e) {
            throwIf401ScopeNotAllowed(e);
            logWbApiError("список складов продавца", e, WbApiEventType.FBS_WAREHOUSES_SYNC_CABINET.getUri(), WAREHOUSES_OPERATION);
            throw new RestClientException("Ошибка при получении списка складов продавца: " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw e;
        } catch (Exception e) {
            logIoErrorOrFull("получении списка складов продавца", e);
            throw new RestClientException("Ошибка при получении списка складов продавца: " + e.getMessage(), e);
        }
    }

    private WbFbsStocksResponse getFbsStocksOnce(String apiKey, Long warehouseId, List<Long> chrtIds) {
        HttpHeaders headers = createAuthHeaders(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        WbFbsStocksRequest request = WbFbsStocksRequest.builder().chrtIds(chrtIds).build();
        HttpEntity<WbFbsStocksRequest> entity = new HttpEntity<>(request, headers);
        String url = WbApiEventType.FBS_STOCKS_CABINET.getDefaultUrl() + "/" + warehouseId;
        logWbApiCall(url, "остатки FBS на складе продавца", warehouseId);

        ResponseEntity<WbFbsStocksResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                WbFbsStocksResponse.class
        );
        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("Ошибка от WB API: статус={}", response.getStatusCode());
            throw new RestClientException("Ошибка от WB API: " + response.getStatusCode());
        }
        if (response.getBody() == null) {
            log.error("Тело ответа от WB API пустое");
            throw new RestClientException("Тело ответа от WB API пустое");
        }
        return response.getBody();
    }
}
