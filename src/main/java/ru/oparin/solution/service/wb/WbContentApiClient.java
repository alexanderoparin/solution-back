package ru.oparin.solution.service.wb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import ru.oparin.solution.dto.wb.CardsListRequest;
import ru.oparin.solution.dto.wb.CardsListResponse;
import ru.oparin.solution.dto.wb.PingResponse;

/**
 * Клиент для работы с Content API Wildberries.
 * Эндпоинты: карточки товаров, корзина, ping.
 */
@Service
@Slf4j
public class WbContentApiClient extends AbstractWbApiClient {

    private static final String CARDS_LIST_ENDPOINT = "/content/v2/get/cards/list";
    private static final String CARDS_TRASH_ENDPOINT = "/content/v2/get/cards/trash";
    private static final String PING_ENDPOINT = "/ping";
    private static final int DEFAULT_LIMIT = 100;
    private static final int WITH_PHOTO_ALL = -1;

    @Value("${wb.api.content-base-url}")
    private String contentBaseUrl;

    /**
     * Получение списка карточек товаров селлера.
     */
    public CardsListResponse getCardsList(String apiKey, CardsListRequest request) {
        HttpHeaders headers = createAuthHeadersWithBearer(apiKey);
        CardsListRequest requestBody = buildCardsListRequestBody(request);
        HttpEntity<CardsListRequest> entity = new HttpEntity<>(requestBody, headers);
        String url = contentBaseUrl + CARDS_LIST_ENDPOINT;
        
        log.info("Запрос списка карточек товаров: {}", url);
        
        ResponseEntity<String> response = executePostRequest(url, entity);
        return parseCardsListResponse(response);
    }

    /**
     * Получение списка карточек товаров из корзины (trash).
     */
    public CardsListResponse getCardsTrash(String apiKey, CardsListRequest request) {
        HttpHeaders headers = createAuthHeaders(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        CardsListRequest requestBody = buildTrashRequestBody(request);
        HttpEntity<CardsListRequest> entity = new HttpEntity<>(requestBody, headers);
        String url = contentBaseUrl + CARDS_TRASH_ENDPOINT;
        
        log.info("Запрос списка карточек из корзины: {}", url);
        
        ResponseEntity<CardsListResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                CardsListResponse.class
        );
        
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RestClientException("Неожиданный ответ от WB API: " + response.getStatusCode());
        }
        
        return response.getBody();
    }

    /**
     * Проверка подключения к WB API.
     */
    public PingResponse ping(String apiKey) {
        HttpHeaders headers = createAuthHeaders(apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        String url = contentBaseUrl + PING_ENDPOINT;
        
        log.info("Запрос проверки подключения: {}", url);
        
        ResponseEntity<PingResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                PingResponse.class
        );
        
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RestClientException("Неожиданный ответ от WB API: " + response.getStatusCode());
        }
        
        return response.getBody();
    }

    private CardsListRequest buildCardsListRequestBody(CardsListRequest request) {
        CardsListRequest.Cursor cursor = buildCursor(request);
        CardsListRequest.Filter filter = buildFilter(request);
        CardsListRequest.Sort sort = extractSort(request);
        
        CardsListRequest.Settings settings = CardsListRequest.Settings.builder()
                .cursor(cursor)
                .filter(filter)
                .sort(sort)
                .build();
        
        return CardsListRequest.builder()
                .settings(settings)
                .build();
    }

    private CardsListRequest buildTrashRequestBody(CardsListRequest request) {
        CardsListRequest.Cursor cursor = buildCursor(request);
        
        CardsListRequest.Settings settings = CardsListRequest.Settings.builder()
                .cursor(cursor)
                .build();
        
        return CardsListRequest.builder()
                .settings(settings)
                .build();
    }

    private CardsListRequest.Cursor buildCursor(CardsListRequest request) {
        CardsListRequest.Cursor cursorData = extractCursor(request);
        
        Integer limit = cursorData != null && cursorData.getLimit() != null 
                ? cursorData.getLimit() 
                : DEFAULT_LIMIT;
        
        return CardsListRequest.Cursor.builder()
                .limit(limit)
                .nmID(cursorData != null ? cursorData.getNmID() : null)
                .updatedAt(cursorData != null ? cursorData.getUpdatedAt() : null)
                .build();
    }

    private CardsListRequest.Filter buildFilter(CardsListRequest request) {
        CardsListRequest.Filter filterData = extractFilter(request);
        
        if (filterData == null) {
            return CardsListRequest.Filter.builder()
                    .withPhoto(WITH_PHOTO_ALL)
                    .build();
        }
        
        Integer withPhoto = filterData.getWithPhoto() != null 
            ? filterData.getWithPhoto() 
            : WITH_PHOTO_ALL;
        
        return CardsListRequest.Filter.builder()
                .textSearch(filterData.getTextSearch())
                .allowedCategoriesOnly(filterData.getAllowedCategoriesOnly())
                .tagIDs(filterData.getTagIDs())
                .objectIDs(filterData.getObjectIDs())
                .brands(filterData.getBrands())
                .imtID(filterData.getImtID())
                .withPhoto(withPhoto)
                .build();
    }

    private CardsListRequest.Cursor extractCursor(CardsListRequest request) {
        if (request != null && request.getSettings() != null) {
            return request.getSettings().getCursor();
        }
        return null;
    }

    private CardsListRequest.Filter extractFilter(CardsListRequest request) {
        if (request != null && request.getSettings() != null) {
            return request.getSettings().getFilter();
        }
        return null;
    }

    private CardsListRequest.Sort extractSort(CardsListRequest request) {
        if (request != null && request.getSettings() != null) {
            return request.getSettings().getSort();
        }
        return null;
    }

    private CardsListResponse parseCardsListResponse(ResponseEntity<String> response) {
        try {
            return objectMapper.readValue(response.getBody(), CardsListResponse.class);
        } catch (Exception e) {
            log.error("Ошибка при парсинге ответа от WB API: {}", e.getMessage());
            throw new RestClientException("Ошибка при парсинге ответа от WB API: " + e.getMessage(), e);
        }
    }
}

