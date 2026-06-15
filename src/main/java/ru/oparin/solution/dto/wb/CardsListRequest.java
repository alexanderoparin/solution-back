package ru.oparin.solution.dto.wb;

import lombok.*;

import java.util.List;

/**
 * DTO для запроса списка карточек товаров WB API.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardsListRequest {
    
    /**
     * Настройки запроса.
     */
    private Settings settings;
    
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Settings {
        /**
         * Настройки курсора для пагинации.
         */
        private Cursor cursor;
        
        /**
         * Фильтры.
         */
        private Filter filter;
        
        /**
         * Сортировка.
         */
        private Sort sort;
    }
    
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Cursor {
        /**
         * Количество элементов на странице.
         */
        private Integer limit;
        
        /**
         * ID последнего элемента для пагинации.
         */
        private Long nmID;
        
        /**
         * Дата обновления последнего элемента для пагинации.
         */
        private String updatedAt;
    }
    
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Filter {
        /**
         * Поиск по тексту.
         */
        private String textSearch;
        
        /**
         * Только разрешенные категории.
         */
        private Boolean allowedCategoriesOnly;
        
        /**
         * Массив ID ярлыков.
         */
        private List<Integer> tagIDs;
        
        /**
         * Массив ID предметов.
         */
        private List<Integer> objectIDs;
        
        /**
         * Массив названий брендов.
         */
        private List<String> brands;
        
        /**
         * ID карточки товара.
         */
        private Long imtID;
        
        /**
         * Фильтр по наличию фото (POST /content/v2/get/cards/list, поле settings.filter.withPhoto):
         * <ul>
         *   <li>{@code -1} — любые карточки товаров</li>
         *   <li>{@code 0} или отсутствие параметра — любые карточки (с фото и без)</li>
         *   <li>{@code 1} — только карточки с фото</li>
         *   <li>{@code 2} — только карточки без фото</li>
         * </ul>
         * В запросах сервиса по умолчанию передаётся {@code -1}.
         */
        private Integer withPhoto;
    }
    
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Sort {
        /**
         * Направление сортировки.
         */
        private Boolean ascending;
    }
}

