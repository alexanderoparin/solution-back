package ru.oparin.solution.service;

import lombok.experimental.UtilityClass;
import ru.oparin.solution.dto.wb.CardsListRequest;

/**
 * Билдер для создания запросов списка карточек.
 */
@UtilityClass
public class CardsListRequestBuilder {

    private static final int DEFAULT_LIMIT = 100;

    /**
     * Создает запрос с значениями по умолчанию.
     */
    public CardsListRequest createDefault() {
        CardsListRequest.Cursor cursor = CardsListRequest.Cursor.builder()
                .limit(DEFAULT_LIMIT)
                .build();

        CardsListRequest.Settings settings = CardsListRequest.Settings.builder()
                .cursor(cursor)
                .build();

        return CardsListRequest.builder()
                .settings(settings)
                .build();
    }

    /**
     * Создает запрос с дефолтными значениями, если они не указаны.
     */
    public CardsListRequest withDefaults(CardsListRequest request) {
        if (request != null && request.getSettings() != null) {
            return request;
        }
        return createDefault();
    }

    /**
     * Создает запрос для поиска по артикулу.
     */
    public CardsListRequest createSearchRequest(String vendorCode) {
        CardsListRequest.Cursor cursor = CardsListRequest.Cursor.builder()
                .limit(DEFAULT_LIMIT)
                .build();

        CardsListRequest.Filter filter = CardsListRequest.Filter.builder()
                .textSearch(vendorCode)
                .build();

        CardsListRequest.Settings settings = CardsListRequest.Settings.builder()
                .cursor(cursor)
                .filter(filter)
                .build();

        return CardsListRequest.builder()
                .settings(settings)
                .build();
    }
}

