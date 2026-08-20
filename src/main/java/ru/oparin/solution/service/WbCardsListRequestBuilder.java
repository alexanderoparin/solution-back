package ru.oparin.solution.service;

import lombok.experimental.UtilityClass;
import ru.oparin.solution.dto.wb.WbCardsListRequest;

/**
 * Билдер для создания запросов списка карточек.
 */
@UtilityClass
public class WbCardsListRequestBuilder {

    private static final int DEFAULT_LIMIT = 100;

    /**
     * Создает запрос с значениями по умолчанию.
     */
    public WbCardsListRequest createDefault() {
        WbCardsListRequest.Cursor cursor = WbCardsListRequest.Cursor.builder()
                .limit(DEFAULT_LIMIT)
                .build();

        WbCardsListRequest.Settings settings = WbCardsListRequest.Settings.builder()
                .cursor(cursor)
                .build();

        return WbCardsListRequest.builder()
                .settings(settings)
                .build();
    }

    /**
     * Создает запрос с дефолтными значениями, если они не указаны.
     */
    public WbCardsListRequest withDefaults(WbCardsListRequest request) {
        if (request != null && request.getSettings() != null) {
            return request;
        }
        return createDefault();
    }

    /**
     * Создает запрос для поиска по артикулу.
     */
    public WbCardsListRequest createSearchRequest(String vendorCode) {
        WbCardsListRequest.Cursor cursor = WbCardsListRequest.Cursor.builder()
                .limit(DEFAULT_LIMIT)
                .build();

        WbCardsListRequest.Filter filter = WbCardsListRequest.Filter.builder()
                .textSearch(vendorCode)
                .build();

        WbCardsListRequest.Settings settings = WbCardsListRequest.Settings.builder()
                .cursor(cursor)
                .filter(filter)
                .build();

        return WbCardsListRequest.builder()
                .settings(settings)
                .build();
    }
}

