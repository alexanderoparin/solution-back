package ru.oparin.solution.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO для сводных метрик по периоду.
 * Содержит агрегированные метрики общей воронки и рекламной воронки за период.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggregatedMetricsDto {
    /**
     * Переходы в карточку товара.
     */
    private Integer transitions;
    
    /**
     * Количество товаров, добавленных в корзину.
     */
    private Integer cart;
    
    /**
     * Количество заказанных товаров.
     */
    private Integer orders;
    
    /**
     * Сумма заказов в рублях.
     */
    private BigDecimal ordersAmount;
    
    /**
     * Конверсия в корзину в процентах.
     */
    private BigDecimal cartConversion;
    
    /**
     * Конверсия в заказ в процентах.
     */
    private BigDecimal orderConversion;
    
    /**
     * Просмотры рекламных объявлений.
     */
    private Integer views;
    
    /**
     * Клики по рекламным объявлениям.
     */
    private Integer clicks;
    
    /**
     * Затраты на рекламу в рублях.
     */
    private BigDecimal costs;
    
    /**
     * Стоимость клика (CPC - Cost Per Click) в рублях.
     */
    private BigDecimal cpc;
    
    /**
     * CTR (Click-Through Rate) - процент кликов от показов.
     */
    private BigDecimal ctr;
    
    /**
     * Стоимость заказа (CPO - Cost Per Order) в рублях.
     */
    private BigDecimal cpo;
    
    /**
     * ДРР (Доля расходов на рекламу) - процент затрат от суммы заказов.
     */
    private BigDecimal drr;
}

