package ru.oparin.solution.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.oparin.solution.dto.wb.CardsListRequest;
import ru.oparin.solution.dto.wb.CardsListResponse;
import ru.oparin.solution.dto.wb.PingResponse;
import ru.oparin.solution.dto.wb.SellerInfoResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * Клиент для работы с WB API.
 */
@Service
@Slf4j
public class WbApiClient {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final int DEFAULT_LIMIT = 100;
    private static final int WITH_PHOTO_ALL = -1;
    private static final String CARDS_LIST_ENDPOINT = "/content/v2/get/cards/list";
    private static final String CARDS_TRASH_ENDPOINT = "/content/v2/get/cards/trash";
    private static final String PING_ENDPOINT = "/ping";
    private static final String SELLER_INFO_URL = "https://common-api.wildberries.ru/api/v1/seller-info";

    @Value("${wb.api.base-url}")
    private String baseUrl;
    
    @Value("${wb.api.content-base-url}")
    private String contentBaseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public WbApiClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false
        );
    }

    /**
     * Валидация WB API ключа через тестовый запрос.
     *
     * @param apiKey API ключ для проверки
     * @return true если ключ валиден, false в противном случае
     */
    public boolean validateApiKey(String apiKey) {
        try {
            HttpHeaders headers = createAuthHeaders(apiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            String url = baseUrl + "/api/v2/supplier/warehouses";
            
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
     * Получение списка карточек товаров селлера.
     * 
     * @param apiKey API ключ селлера
     * @param request параметры запроса
     * @return ответ со списком карточек товаров
     * @throws RestClientException при ошибке запроса к WB API
     */
    public CardsListResponse getCardsList(String apiKey, CardsListRequest request) {
        try {
            HttpHeaders headers = createAuthHeadersWithBearer(apiKey);
            Map<String, Object> requestBody = buildCardsListRequestBody(request);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            String url = contentBaseUrl + CARDS_LIST_ENDPOINT;
            
            ResponseEntity<String> response = executePostRequest(url, entity);
            return parseCardsListResponse(response);
            
        } catch (RestClientException e) {
            log.error("Ошибка при получении списка карточек: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Неожиданная ошибка: {}", e.getMessage());
            throw new RestClientException("Неожиданная ошибка: " + e.getMessage(), e);
        }
    }

    /**
     * Проверка подключения к WB API.
     * 
     * @param apiKey API ключ для проверки
     * @return ответ с временной меткой и статусом подключения
     * @throws RestClientException при ошибке подключения
     */
    public PingResponse ping(String apiKey) {
        try {
            HttpHeaders headers = createAuthHeaders(apiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            String url = contentBaseUrl + PING_ENDPOINT;
            
            ResponseEntity<PingResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    PingResponse.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Подключение к WB API успешно: status={}, ts={}", 
                        response.getBody().getStatus(), response.getBody().getTs());
                return response.getBody();
            } else {
                throw new RestClientException("Неожиданный ответ от WB API: " + response.getStatusCode());
            }
            
        } catch (RestClientException e) {
            log.error("Ошибка при проверке подключения к WB API: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Получение списка карточек товаров из корзины (trash).
     * 
     * @param apiKey API ключ селлера
     * @param request параметры запроса
     * @return ответ со списком карточек товаров из корзины
     * @throws RestClientException при ошибке запроса к WB API
     */
    public CardsListResponse getCardsTrash(String apiKey, CardsListRequest request) {
        try {
            HttpHeaders headers = createAuthHeaders(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> requestBody = buildTrashRequestBody(request);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            String url = contentBaseUrl + CARDS_TRASH_ENDPOINT;
            
            ResponseEntity<CardsListResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    CardsListResponse.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Integer total = response.getBody().getCursor() != null 
                    ? response.getBody().getCursor().getTotal() 
                    : 0;
                int cardsSize = response.getBody().getCards() != null 
                    ? response.getBody().getCards().size() 
                    : 0;
                log.info("Успешно получено карточек из корзины: total={}, cards.size()={}", total, cardsSize);
                return response.getBody();
            } else {
                throw new RestClientException("Неожиданный ответ от WB API: " + response.getStatusCode());
            }
            
        } catch (RestClientException e) {
            log.error("Ошибка при получении списка карточек из корзины: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Получение информации о продавце.
     * 
     * @param apiKey API ключ для проверки
     * @return информация о продавце
     * @throws RestClientException при ошибке запроса к WB API
     */
    public SellerInfoResponse getSellerInfo(String apiKey) {
        try {
            HttpHeaders headers = createAuthHeaders(apiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<SellerInfoResponse> response = restTemplate.exchange(
                    SELLER_INFO_URL,
                    HttpMethod.GET,
                    entity,
                    SellerInfoResponse.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Информация о продавце получена: name={}, sid={}", 
                        response.getBody().getName(), response.getBody().getSid());
                return response.getBody();
            } else {
                throw new RestClientException("Неожиданный ответ от WB API: " + response.getStatusCode());
            }
            
        } catch (RestClientException e) {
            log.error("Ошибка при получении информации о продавце: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Создает заголовки с авторизацией (без префикса Bearer).
     */
    private HttpHeaders createAuthHeaders(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", apiKey);
        return headers;
    }

    /**
     * Создает заголовки с авторизацией и префиксом Bearer.
     */
    private HttpHeaders createAuthHeadersWithBearer(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        String authHeader = apiKey.startsWith(BEARER_PREFIX) ? apiKey : BEARER_PREFIX + apiKey;
        headers.set("Authorization", authHeader);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /**
     * Строит тело запроса для получения списка карточек.
     */
    private Map<String, Object> buildCardsListRequestBody(CardsListRequest request) {
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> settings = new HashMap<>();
        
        settings.put("cursor", buildCursor(request));
        settings.put("filter", buildFilter(request));
        
        CardsListRequest.Sort sort = extractSort(request);
        if (sort != null && sort.getAscending() != null) {
            Map<String, Object> sortMap = new HashMap<>();
            sortMap.put("ascending", sort.getAscending());
            settings.put("sort", sortMap);
        }
        
        requestBody.put("settings", settings);
        return requestBody;
    }

    /**
     * Строит тело запроса для получения карточек из корзины.
     */
    private Map<String, Object> buildTrashRequestBody(CardsListRequest request) {
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> settings = new HashMap<>();
        settings.put("cursor", buildCursor(request));
        requestBody.put("settings", settings);
        return requestBody;
    }

    /**
     * Строит объект курсора для пагинации.
     */
    private Map<String, Object> buildCursor(CardsListRequest request) {
        CardsListRequest.Cursor cursorData = extractCursor(request);
        
        Map<String, Object> cursor = new HashMap<>();
        if (cursorData != null) {
            if (cursorData.getLimit() != null) {
                cursor.put("limit", cursorData.getLimit());
            }
            if (cursorData.getNmID() != null) {
                cursor.put("nmID", cursorData.getNmID());
            }
            if (cursorData.getUpdatedAt() != null) {
                cursor.put("updatedAt", cursorData.getUpdatedAt());
            }
        }
        
        // Если limit не установлен, используем значение по умолчанию
        if (!cursor.containsKey("limit")) {
            cursor.put("limit", DEFAULT_LIMIT);
        }
        
        return cursor;
    }

    /**
     * Строит объект фильтра.
     */
    private Map<String, Object> buildFilter(CardsListRequest request) {
        CardsListRequest.Filter filterData = extractFilter(request);
        Map<String, Object> filter = new HashMap<>();
        
        if (filterData != null) {
            addFilterIfNotNull(filter, "textSearch", filterData.getTextSearch());
            addFilterIfNotNull(filter, "allowedCategoriesOnly", filterData.getAllowedCategoriesOnly());
            addFilterIfNotEmpty(filter, "tagIDs", filterData.getTagIDs());
            addFilterIfNotEmpty(filter, "objectIDs", filterData.getObjectIDs());
            addFilterIfNotEmpty(filter, "brands", filterData.getBrands());
            addFilterIfNotNull(filter, "imtID", filterData.getImtID());
        }
        
        // Всегда добавляем withPhoto (по умолчанию -1 для получения всех карточек)
        Integer withPhoto = filterData != null && filterData.getWithPhoto() != null 
            ? filterData.getWithPhoto() 
            : WITH_PHOTO_ALL;
        filter.put("withPhoto", withPhoto);
        
        return filter;
    }

    /**
     * Добавляет значение в фильтр, если оно не null.
     */
    private void addFilterIfNotNull(Map<String, Object> filter, String key, Object value) {
        if (value != null) {
            filter.put(key, value);
        }
    }

    /**
     * Добавляет коллекцию в фильтр, если она не пуста.
     */
    private void addFilterIfNotEmpty(Map<String, Object> filter, String key, java.util.Collection<?> value) {
        if (value != null && !value.isEmpty()) {
            filter.put(key, value);
        }
    }

    /**
     * Извлекает курсор из запроса.
     */
    private CardsListRequest.Cursor extractCursor(CardsListRequest request) {
        if (request != null && request.getSettings() != null) {
            return request.getSettings().getCursor();
        }
        return null;
    }

    /**
     * Извлекает фильтр из запроса.
     */
    private CardsListRequest.Filter extractFilter(CardsListRequest request) {
        if (request != null && request.getSettings() != null) {
            return request.getSettings().getFilter();
        }
        return null;
    }

    /**
     * Извлекает сортировку из запроса.
     */
    private CardsListRequest.Sort extractSort(CardsListRequest request) {
        if (request != null && request.getSettings() != null) {
            return request.getSettings().getSort();
        }
        return null;
    }

    /**
     * Выполняет POST запрос и возвращает строковый ответ.
     */
    private ResponseEntity<String> executePostRequest(String url, HttpEntity<Map<String, Object>> entity) {
        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
        );
        
        validateResponse(response);
        return response;
    }

    /**
     * Валидирует ответ от WB API.
     */
    private void validateResponse(ResponseEntity<String> response) {
        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("Ошибка от WB API: статус={}, тело={}", 
                    response.getStatusCode(), response.getBody());
            throw new RestClientException("Ошибка от WB API: " + response.getStatusCode() + " - " + response.getBody());
        }
        
        if (response.getBody() == null || response.getBody().isEmpty()) {
            log.error("Тело ответа от WB API пустое");
            throw new RestClientException("Тело ответа от WB API пустое");
        }
    }

    /**
     * Парсит ответ от WB API в объект CardsListResponse.
     */
    private CardsListResponse parseCardsListResponse(ResponseEntity<String> response) {
        try {
            CardsListResponse cardsListResponse = objectMapper.readValue(
                    response.getBody(), 
                    CardsListResponse.class
            );
            
            if (cardsListResponse == null) {
                throw new RestClientException("Не удалось распарсить ответ от WB API - объект равен null");
            }
            
            logResponseInfo(cardsListResponse);
            return cardsListResponse;
            
        } catch (Exception e) {
            log.error("Ошибка при парсинге ответа от WB API: {}", e.getMessage());
            throw new RestClientException("Ошибка при парсинге ответа от WB API: " + e.getMessage(), e);
        }
    }

    /**
     * Логирует информацию о результате запроса.
     */
    private void logResponseInfo(CardsListResponse response) {
        Integer total = response.getCursor() != null 
            ? response.getCursor().getTotal() 
            : 0;
        int cardsSize = response.getCards() != null 
            ? response.getCards().size() 
            : 0;
        log.info("WB API: получено карточек total={}, в ответе={}", total, cardsSize);
    }
}
