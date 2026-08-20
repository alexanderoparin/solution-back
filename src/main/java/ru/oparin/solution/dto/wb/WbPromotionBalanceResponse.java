package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

/**
 * Ответ GET /adv/v1/balance.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WbPromotionBalanceResponse {
    private Integer balance;
    private Integer net;
    private Integer bonus;
    private List<Cashback> cashbacks;

    /**
     * Бонусы для источника type=3. Если WB не прислал поле — 0 (не null).
     */
    public int resolveBonusRub() {
        return bonus != null ? bonus : 0;
    }

    /**
     * Сумма активных промо-бонусов ({@code cashbacks[].sum}).
     */
    public int resolveCashbackRub() {
        if (cashbacks == null || cashbacks.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Cashback cashback : cashbacks) {
            if (cashback != null && cashback.getSum() != null) {
                total += cashback.getSum();
            }
        }
        return total;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Cashback {
        private Integer sum;
        private Integer percent;
        @JsonProperty("expiration_date")
        private String expirationDate;
    }
}
