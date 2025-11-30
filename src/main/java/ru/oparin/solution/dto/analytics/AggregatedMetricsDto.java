package ru.oparin.solution.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO для сводных метрик по периоду.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggregatedMetricsDto {
    // Метрики воронки
    private Integer transitions; // Переходы в карточку
    private Integer cart; // Положили в корзину, шт
    private Integer orders; // Заказали товаров, шт
    private BigDecimal ordersAmount; // Заказали на сумму, руб
    private BigDecimal cartConversion; // Конверсия в корзину, %
    private BigDecimal orderConversion; // Конверсия в заказ, %
    
    // Метрики рекламы
    private Integer views; // Просмотры
    private Integer clicks; // Клики
    private BigDecimal costs; // Затраты, руб
    private BigDecimal cpc; // СРС, руб
    private BigDecimal ctr; // CTR, %
    private BigDecimal cpo; // СРО, руб
    private BigDecimal drr; // ДРР, %
}

