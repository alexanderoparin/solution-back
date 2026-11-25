package ru.oparin.solution.dto.analytics;

import lombok.*;

import java.math.BigDecimal;

/**
 * Строка рейтинга товаров на дашборде.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardRankingItemDto {

    private int rank;
    private Long nmId;
    private String photoTm;
    private String title;
    private String vendorCode;
    private Integer orders;
    private BigDecimal ordersAmount;
    /**
     * Процент роста/падения; {@code null}, если корректно посчитать нельзя (деление на ноль).
     */
    private BigDecimal changePercent;
}
