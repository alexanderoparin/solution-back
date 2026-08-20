package ru.oparin.solution.service.wb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import ru.oparin.solution.dto.wb.WbCardsListRequest;
import ru.oparin.solution.dto.wb.WbCardsListResponse;
import ru.oparin.solution.dto.wb.WbContentMediaSaveRequest;
import ru.oparin.solution.dto.wb.WbPingResponse;
import ru.oparin.solution.model.WbApiEventType;

import java.util.List;

/**
 * Клиент для работы с Content API Wildberries.
 * Эндпоинты: карточки товаров, корзина, ping.
 * Категория WB API: Контент.
 */
@Service
@Slf4j
public class WbContentApiClient extends AbstractWbApiClient {

    @Override
    protected WbApiCategory getApiCategory() {
        return WbApiCategory.CONTENT;
    }

    private static final int DEFAULT_LIMIT = 100;
    /** Любые карточки товаров ({@code withPhoto: -1} в WB API). */
    private static final int WITH_PHOTO_ALL = -1;

    /**
     * Получение списка карточек товаров селлера.
     * При таймауте или ошибке соединения выполняются ретраи.
     */
    public WbCardsListResponse getCardsList(String apiKey, WbCardsListRequest request) {
        return executeWithConnectionRetry("список карточек товаров", () -> getCardsListOnce(apiKey, request));
    }

    private WbCardsListResponse getCardsListOnce(String apiKey, WbCardsListRequest request) {
        HttpHeaders headers = createAuthHeadersWithBearer(apiKey);
        WbCardsListRequest requestBody = buildWbCardsListRequestBody(request);
        HttpEntity<WbCardsListRequest> entity = new HttpEntity<>(requestBody, headers);
        String url = WbApiEventType.CONTENT_CARDS_LIST_PAGE.getDefaultUrl();
        logWbApiCall(url, "список карточек товаров");

        try {
            ResponseEntity<String> response = executePostRequest(url, entity);
            return parseWbCardsListResponse(response);
        } catch (HttpClientErrorException e) {
            throwIf401ScopeNotAllowed(e);
            logWbApiError("список карточек товаров WB", e);
            throw new RestClientException("Ошибка при получении списка карточек товаров: " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw e;
        } catch (Exception e) {
            logIoErrorOrFull("получении списка карточек товаров", e);
            throw new RestClientException("Ошибка при получении списка карточек товаров: " + e.getMessage(), e);
        }
    }

    /**
     * Получение списка карточек товаров из корзины (trash).
     * При таймауте или ошибке соединения выполняются ретраи.
     */
    public WbCardsListResponse getCardsTrash(String apiKey, WbCardsListRequest request) {
        return executeWithConnectionRetry("карточки из корзины", () -> getCardsTrashOnce(apiKey, request));
    }

    private WbCardsListResponse getCardsTrashOnce(String apiKey, WbCardsListRequest request) {
        HttpHeaders headers = createAuthHeaders(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        WbCardsListRequest requestBody = buildTrashRequestBody(request);
        HttpEntity<WbCardsListRequest> entity = new HttpEntity<>(requestBody, headers);
        String url = WbApiEventType.CONTENT_CARDS_TRASH.getDefaultUrl();
        logWbApiCall(url, "карточки из корзины");

        try {
            ResponseEntity<WbCardsListResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    WbCardsListResponse.class
            );
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RestClientException("Неожиданный ответ от WB API: " + response.getStatusCode());
            }
            return response.getBody();
        } catch (HttpClientErrorException e) {
            throwIf401ScopeNotAllowed(e);
            logWbApiError("список карточек из корзины WB", e);
            throw new RestClientException("Ошибка при получении списка карточек из корзины: " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw e;
        } catch (Exception e) {
            logIoErrorOrFull("получении списка карточек из корзины", e);
            throw new RestClientException("Ошибка при получении списка карточек из корзины: " + e.getMessage(), e);
        }
    }

    /**
     * Загрузка медиафайла в карточку товара (POST /content/v3/media/file).
     *
     * @param apiKey      токен кабинета
     * @param nmId        артикул WB
     * @param photoNumber номер фото (1 — главное)
     * @param fileBytes   содержимое файла
     * @param fileName    имя файла для multipart
     */
    public void uploadMediaFile(String apiKey, Long nmId, int photoNumber, byte[] fileBytes, String fileName) {
        executeWithConnectionRetry("загрузка медиафайла",
                () -> {
                    uploadMediaFileOnce(apiKey, nmId, photoNumber, fileBytes, fileName);
                    return null;
                });
    }

    private void uploadMediaFileOnce(String apiKey, Long nmId, int photoNumber, byte[] fileBytes, String fileName) {
        // По сваггеру WB: nmId и номер фото — в headers; в body только uploadfile.
        // Content-Type multipart не задаём вручную — RestTemplate сам добавит boundary.
        HttpHeaders headers = new HttpHeaders();
        String auth = apiKey.startsWith(BEARER_PREFIX) ? apiKey : BEARER_PREFIX + apiKey;
        headers.set(HttpHeaders.AUTHORIZATION, auth);
        headers.set("X-Nm-Id", String.valueOf(nmId));
        headers.set("X-Photo-Number", String.valueOf(photoNumber));

        ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return fileName != null && !fileName.isBlank() ? fileName : "photo.jpg";
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("uploadfile", fileResource);

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        String url = WbApiEventType.CONTENT_MEDIA_FILE.getDefaultUrl();
        logWbApiCall(url, "загрузка медиафайла nmId=" + nmId + " photoNumber=" + photoNumber);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RestClientException("Неожиданный ответ от WB API: " + response.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            throwIf401ScopeNotAllowed(e);
            logWbApiError("загрузка медиафайла WB", e);
            throw new RestClientException("Ошибка при загрузке медиафайла: " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw e;
        } catch (Exception e) {
            logIoErrorOrFull("загрузке медиафайла", e);
            throw new RestClientException("Ошибка при загрузке медиафайла: " + e.getMessage(), e);
        }
    }

    /**
     * Замена набора медиа карточки по URL (POST /content/v3/media/save).
     * Первый URL в списке становится главным фото; набор полностью заменяется.
     *
     * @param apiKey токен кабинета
     * @param nmId   артикул WB
     * @param urls   упорядоченный список URL медиа
     */
    public void saveMediaByUrls(String apiKey, Long nmId, List<String> urls) {
        executeWithConnectionRetry("сохранение медиа по ссылкам",
                () -> {
                    saveMediaByUrlsOnce(apiKey, nmId, urls);
                    return null;
                });
    }

    private void saveMediaByUrlsOnce(String apiKey, Long nmId, List<String> urls) {
        WbContentMediaSaveRequest request = WbContentMediaSaveRequest.builder()
                .nmId(nmId)
                .data(urls)
                .build();
        HttpHeaders headers = createAuthHeadersWithBearer(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<WbContentMediaSaveRequest> entity = new HttpEntity<>(request, headers);
        String url = WbApiEventType.CONTENT_MEDIA_SAVE.getDefaultUrl();
        logWbApiCall(url, "сохранение медиа по ссылкам nmId=" + nmId + " count=" + (urls != null ? urls.size() : 0));

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RestClientException("Неожиданный ответ от WB API: " + response.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            throwIf401ScopeNotAllowed(e);
            logWbApiError("сохранение медиа по ссылкам WB", e);
            throw new RestClientException("Ошибка при сохранении медиа: " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw e;
        } catch (Exception e) {
            logIoErrorOrFull("сохранении медиа по ссылкам", e);
            throw new RestClientException("Ошибка при сохранении медиа: " + e.getMessage(), e);
        }
    }

    /**
     * Проверка подключения к WB API.
     * При таймауте или ошибке соединения выполняются ретраи.
     */
    public WbPingResponse ping(String apiKey) {
        return executeWithConnectionRetry("проверка подключения (ping)", () -> pingOnce(apiKey));
    }

    private WbPingResponse pingOnce(String apiKey) {
        HttpHeaders headers = createAuthHeaders(apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        String url = WbApiEventType.CONTENT_CARDS_LIST_PAGE.getBaseUrl().getPingUrl();
        logWbApiCall(url, "проверка подключения (ping)");

        try {
            ResponseEntity<WbPingResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    WbPingResponse.class
            );
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RestClientException("Неожиданный ответ от WB API: " + response.getStatusCode());
            }
            return response.getBody();
        } catch (HttpClientErrorException e) {
            throwIf401ScopeNotAllowed(e);
            logWbApiError("ping WB API", e);
            throw new RestClientException("Ошибка при проверке подключения к WB API: " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw e;
        } catch (Exception e) {
            logIoErrorOrFull("проверке подключения к WB API (ping)", e);
            throw new RestClientException("Ошибка при проверке подключения к WB API: " + e.getMessage(), e);
        }
    }

    private WbCardsListRequest buildWbCardsListRequestBody(WbCardsListRequest request) {
        WbCardsListRequest.Cursor cursor = buildCursor(request);
        WbCardsListRequest.Filter filter = buildFilter(request);
        WbCardsListRequest.Sort sort = extractSort(request);
        
        WbCardsListRequest.Settings settings = WbCardsListRequest.Settings.builder()
                .cursor(cursor)
                .filter(filter)
                .sort(sort)
                .build();
        
        return WbCardsListRequest.builder()
                .settings(settings)
                .build();
    }

    private WbCardsListRequest buildTrashRequestBody(WbCardsListRequest request) {
        WbCardsListRequest.Cursor cursor = buildCursor(request);
        
        WbCardsListRequest.Settings settings = WbCardsListRequest.Settings.builder()
                .cursor(cursor)
                .build();
        
        return WbCardsListRequest.builder()
                .settings(settings)
                .build();
    }

    private WbCardsListRequest.Cursor buildCursor(WbCardsListRequest request) {
        WbCardsListRequest.Cursor cursorData = extractCursor(request);
        
        Integer limit = cursorData != null && cursorData.getLimit() != null 
                ? cursorData.getLimit() 
                : DEFAULT_LIMIT;
        
        return WbCardsListRequest.Cursor.builder()
                .limit(limit)
                .nmID(cursorData != null ? cursorData.getNmID() : null)
                .updatedAt(cursorData != null ? cursorData.getUpdatedAt() : null)
                .build();
    }

    private WbCardsListRequest.Filter buildFilter(WbCardsListRequest request) {
        WbCardsListRequest.Filter filterData = extractFilter(request);
        
        if (filterData == null) {
            return WbCardsListRequest.Filter.builder()
                    .withPhoto(WITH_PHOTO_ALL)
                    .build();
        }
        
        Integer withPhoto = filterData.getWithPhoto() != null 
            ? filterData.getWithPhoto() 
            : WITH_PHOTO_ALL;
        
        return WbCardsListRequest.Filter.builder()
                .textSearch(filterData.getTextSearch())
                .allowedCategoriesOnly(filterData.getAllowedCategoriesOnly())
                .tagIDs(filterData.getTagIDs())
                .objectIDs(filterData.getObjectIDs())
                .brands(filterData.getBrands())
                .imtID(filterData.getImtID())
                .withPhoto(withPhoto)
                .build();
    }

    private WbCardsListRequest.Cursor extractCursor(WbCardsListRequest request) {
        if (request != null && request.getSettings() != null) {
            return request.getSettings().getCursor();
        }
        return null;
    }

    private WbCardsListRequest.Filter extractFilter(WbCardsListRequest request) {
        if (request != null && request.getSettings() != null) {
            return request.getSettings().getFilter();
        }
        return null;
    }

    private WbCardsListRequest.Sort extractSort(WbCardsListRequest request) {
        if (request != null && request.getSettings() != null) {
            return request.getSettings().getSort();
        }
        return null;
    }

    private WbCardsListResponse parseWbCardsListResponse(ResponseEntity<String> response) {
        try {
            return objectMapper.readValue(response.getBody(), WbCardsListResponse.class);
        } catch (Exception e) {
            log.error("Ошибка при парсинге ответа от WB API: {}", e.getMessage());
            throw new RestClientException("Ошибка при парсинге ответа от WB API: " + e.getMessage(), e);
        }
    }
}

