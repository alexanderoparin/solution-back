package ru.oparin.solution.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * DTO для остатков товара на складе.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockDto {
    /**
     * Название склада.
     */
    private String warehouseName;
    
    /**
     * Количество товара на складе.
     */
    private Integer amount;
    
    /**
     * Дата и время последнего обновления остатков.
     */
    private LocalDateTime updatedAt;
}

