package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * DTO для ответа списка карточек товаров WB API.
 * 
 * Документация: https://dev.wildberries.ru/openapi/work-with-products#tag/Kartochki-tovarov/paths/~1content~1v2~1get~1cards~1list/post
 * 
 * Структура ответа согласно документации:
 * {
 *   "cards": [
 *     {
 *       "nmID": 12345678,
 *       "imtID": 123654789,
 *       "nmUUID": "01bda0b1-5c0b-736c-b2be-d0a6543e9be",
 *       "subjectID": 7771,
 *       "subjectName": "AKF системы",
 *       "vendorCode": "wb7f6mumjr1",
 *       "brand": "Тест",
 *       "title": "Тест-система",
 *       "description": "Тестовое описание",
 *       "needKiz": false,
 *       "photos": [...],
 *       "video": "...",
 *       "wholesale": {...},
 *       "dimensions": {...},
 *       "characteristics": [...],
 *       "sizes": [...],
 *       "tags": [...],
 *       "createdAt": "2023-12-06T11:17:00.96577Z",
 *       "updatedAt": "2023-12-06T11:17:00.96577Z"
 *     }
 *   ],
 *   "cursor": {
 *     "total": 1,
 *     "nmID": 123654123,
 *     "updatedAt": "2023-12-06T11:17:00.96577Z"
 *   }
 * }
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CardsListResponse {
    
    /**
     * Список карточек товаров.
     * Каждая карточка содержит:
     * - nmID, imtID, nmUUID - идентификаторы
     * - subjectID, subjectName - категория
     * - vendorCode, brand, title, description - информация о товаре
     * - photos, video - медиафайлы
     * - wholesale, dimensions - оптовые цены и габариты
     * - characteristics - характеристики товара
     * - sizes - размеры с баркодами (skus)
     * - tags - ярлыки
     * - createdAt, updatedAt - даты создания и обновления
     */
    private List<Map<String, Object>> cards;
    
    /**
     * Курсор для пагинации (содержит total, nmID и updatedAt).
     */
    private Cursor cursor;
    
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Cursor {
        /**
         * Общее количество карточек.
         */
        private Integer total;
        
        /**
         * ID последнего элемента (для пагинации).
         */
        private Long nmID;
        
        /**
         * Дата обновления последнего элемента (для пагинации).
         */
        private String updatedAt;
    }
}

