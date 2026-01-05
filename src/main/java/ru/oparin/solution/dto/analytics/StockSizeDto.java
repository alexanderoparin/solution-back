package ru.oparin.solution.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO для остатков товара по размерам на складе.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockSizeDto {
    /**
     * Технический размер (techSize, например "L").
     */
    private String techSize;
    
    /**
     * Российский размер (wbSize, например "48").
     */
    private String wbSize;
    
    /**
     * Количество товара данного размера на складе.
     */
    private Integer amount;
}


