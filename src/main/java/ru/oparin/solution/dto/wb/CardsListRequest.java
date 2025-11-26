package ru.oparin.solution.dto.wb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO для запроса списка карточек товаров WB API.
 * 
 * Документация: https://dev.wildberries.ru/openapi/work-with-products#tag/Kartochki-tovarov/paths/~1content~1v2~1get~1cards~1list/post
 * 
 * Структура запроса согласно документации:
 * {
 *   "settings": {
 *     "cursor": {
 *       "limit": 100,
 *       "nmID": 0,        // для пагинации (из предыдущего ответа)
 *       "updatedAt": ""   // для пагинации (из предыдущего ответа)
 *     },
 *     "filter": {
 *       "withPhoto": -1  // -1 (все), 0 (без фото), 1 (с фото)
 *     }
 *   }
 * }
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardsListRequest {
    
    /**
     * Настройки запроса (cursor и filter).
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
         * Фильтры (опционально).
         */
        private Filter filter;
        
        /**
         * Сортировка (опционально).
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
         * Количество элементов на странице (по умолчанию 100, максимум 1000).
         */
        private Integer limit;
        
        /**
         * ID последнего элемента для пагинации (из предыдущего ответа).
         */
        private Long nmID;
        
        /**
         * Дата обновления последнего элемента для пагинации (из предыдущего ответа).
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
         * Поиск по тексту (артикул, баркод, название).
         */
        private String textSearch;
        
        /**
         * Только разрешенные категории.
         */
        private Boolean allowedCategoriesOnly;
        
        /**
         * Массив ID ярлыков (тегов).
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
         * ID карточки товара (imtID).
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
         * Направление сортировки: true (по возрастанию), false (по убыванию).
         */
        private Boolean ascending;
    }
}

