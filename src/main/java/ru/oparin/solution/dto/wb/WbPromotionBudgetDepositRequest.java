package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public class WbPromotionBudgetDepositRequest {
    private Integer sum;
    /** 0 — счёт, 1 — баланс, 3 — бонусы. */
    private Integer type;
    /**
     * Сумма пополнения промо-бонусами. Только для type 0/1.
     * Не больше {@code percent}% от {@link #sum} и не больше доступных cashbacks.
     */
    @JsonProperty("cashback_sum")
    private Integer cashbackSum;
    /**
     * Процент из GET /adv/v1/balance ({@code cashbacks[].percent}).
     * Обязателен, если задан {@link #cashbackSum}.
     */
    @JsonProperty("cashback_percent")
    private Integer cashbackPercent;
    /** Флаг возврата бюджета в ответе (поле {@code return} в API WB). */
    @JsonProperty("return")
    private Boolean returnBudget;
}
