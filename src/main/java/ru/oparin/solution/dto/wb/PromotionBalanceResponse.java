package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
public class PromotionBalanceResponse {
    private Integer balance;
    private Integer net;
    private Integer bonus;
    private List<Cashback> cashbacks;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Cashback {
        private Integer sum;
        private Integer percent;
        private String expirationDate;
    }
}
