package ru.oparin.solution.dto.wb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
        private java.util.List<Integer> tagIDs;
        
        /**
         * Массив ID предметов.
         */
        private java.util.List<Integer> objectIDs;
        
        /**
         * Массив названий брендов.
         */
        private java.util.List<String> brands;
        
        /**
         * ID карточки товара.
         */
        private Long imtID;
        
        /**
         * Фильтр по наличию фото: -1 (все), 0 (без фото), 1 (с фото).
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

