package ru.oparin.solution.dto.analytics;

import lombok.*;

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
     * Склад помечен как пострадавший (огонёк в UI).
     */
    private Boolean onFire;
    
    /**
     * Количество товара на складе.
     */
    private Integer amount;
    
    /**
     * Дата и время последнего обновления остатков.
     */
    private LocalDateTime updatedAt;
}

