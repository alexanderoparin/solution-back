package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

/**
 * Тело POST /adv/v1/budget/deposit.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PromotionBudgetDepositRequest {
    private Integer sum;
    /** 0 — счёт, 1 — баланс, 3 — бонусы. */
    private Integer type;
    private Boolean returnBudget;
}
