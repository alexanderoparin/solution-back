package ru.oparin.solution.dto;

import lombok.*;

import java.math.BigDecimal;

/**
 * Расчёт доплаты за апгрейд тира (prorate по оставшимся дням).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpgradePackageQuoteDto {

    private Long paymentId;
    private Long currentPlanId;
    private String currentPlanCode;
    private Long targetPlanId;
    private String targetPlanCode;
    private String targetPlanName;
    private BigDecimal currentPriceRub;
    private BigDecimal targetPriceRub;
    private BigDecimal priceDiffRub;
    private long remainingDays;
    private long periodDays;
    /** Доплата к оплате. */
    private BigDecimal amountRub;
    private Integer currentMaxCabinets;
    private Integer targetMaxCabinets;
}
