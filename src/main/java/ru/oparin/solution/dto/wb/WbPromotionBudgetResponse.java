package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

/**
 * Остаток бюджета одной РК: ответ POST /adv/v1/budget/deposit при {@code return=true}
 * и проекция элемента POST /api/advert/v2/budget.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WbPromotionBudgetResponse {
    private Integer cash;
    private Integer netting;
    /** Бюджет кампании, ₽. */
    private Integer total;
}
