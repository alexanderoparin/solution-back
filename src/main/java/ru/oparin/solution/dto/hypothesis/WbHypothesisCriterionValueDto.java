package ru.oparin.solution.dto.hypothesis;

import lombok.*;

import java.math.BigDecimal;

/**
 * Значение одного критерия гипотезы: база / проверка / изменение / успех.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbHypothesisCriterionValueDto {
    private String key;
    private String label;
    private BigDecimal baseline;
    private BigDecimal check;
    private BigDecimal change;
    private BigDecimal changePercent;
    private Boolean improved;
    private boolean lowerIsBetter;
}
