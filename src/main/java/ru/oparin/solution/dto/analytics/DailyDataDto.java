package ru.oparin.solution.dto.analytics;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO для ежедневных данных по артикулу.
 * Содержит метрики общей воронки, рекламной воронки и ценообразования за конкретную дату.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyDataDto {
    /**
     * Дата, за которую собраны данные.
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;
    
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
    
    /**
     * Цена до скидки в рублях.
     */
    private BigDecimal priceBeforeDiscount;
    
    /**
     * Скидка продавца в процентах.
     */
    private Integer sellerDiscount;
    
    /**
     * Цена со скидкой продавца в рублях.
     */
    private BigDecimal priceWithDiscount;
    
    /**
     * Скидка WB Клуба в процентах.
     */
    private Integer wbClubDiscount;
    
    /**
     * Цена со скидкой WB Клуба в рублях.
     */
    private BigDecimal priceWithWbClub;

    /**
     * Цена с СПП (Скидка постоянного покупателя) в рублях.
     * СПП - это скидка, которую дает сам Wildberries постоянным покупателям.
     */
    private BigDecimal priceWithSpp;

    /**
     * СПП (Скидка постоянного покупателя) в рублях.
     * Рассчитывается как: Цена со скидкой WB Клуба - Цена с СПП.
     */
    private BigDecimal sppAmount;

    /**
     * СПП (Скидка постоянного покупателя) в процентах.
     * Рассчитывается как: (СПП руб / Цена со скидкой WB Клуба) × 100.
     */
    private BigDecimal sppPercent;
}

