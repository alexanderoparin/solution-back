package ru.oparin.solution.dto.analytics;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO для рекламной кампании на странице артикула и на странице «Рекламные компании».
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignDto {
    /**
     * ID рекламной кампании (advert_id).
     */
    private Long id;
    
    /**
     * Название рекламной кампании.
     */
    private String name;
    
    /**
     * Название типа кампании.
     */
    private String type;
    
    /**
     * Код статуса кампании (4, 7, 9, 11 и т.д.).
     */
    private Integer status;
    
    /**
     * Название статуса кампании.
     */
    private String statusName;
    
    /**
     * Дата создания кампании.
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    
    /** Показы (агрегат за период). */
    private Integer views;
    /** Клики. */
    private Integer clicks;
    /** CTR, %. */
    private BigDecimal ctr;
    /** CPC, руб. */
    private BigDecimal cpc;
    /** Затраты, руб. */
    private BigDecimal costs;
    /** Добавлено в корзину. */
    private Integer cart;
    /** Заказы. */
    private Integer orders;
}

