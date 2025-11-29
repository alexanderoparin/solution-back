package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * DTO для ответа списка карточек товаров WB API.
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
     */
    private List<CardDto> cards;
    
    /**
     * Курсор для пагинации.
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
         * ID последнего элемента для пагинации.
         */
        private Long nmID;
        
        /**
         * Дата обновления последнего элемента для пагинации.
         */
        private String updatedAt;
    }
}

