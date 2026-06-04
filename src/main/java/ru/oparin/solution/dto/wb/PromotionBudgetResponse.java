package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

/**
 * Ответ GET /adv/v1/budget.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PromotionBudgetResponse {
    private Integer cash;
    private Integer netting;
    /** Бюджет кампании, ₽. */
    private Integer total;
}
