package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.Instant;
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
     * Сумма активных (не истёкших) промо-бонусов ({@code cashbacks[].sum}).
     */
    public int resolveCashbackRub() {
        Cashback primary = resolvePrimaryCashback();
        if (primary == null || primary.getSum() == null) {
            return 0;
        }
        // Если несколько записей — суммируем неистёкшие; percent берём от primary.
        int total = 0;
        for (Cashback cashback : cashbacks) {
            if (isUsable(cashback) && cashback.getSum() != null) {
                total += cashback.getSum();
            }
        }
        return total;
    }

    /**
     * Процент оплаты промо-бонусами за одно пополнение ({@code cashbacks[].percent}).
     * Берётся из записи с наибольшей суммой среди неистёкших.
     */
    public Integer resolveCashbackPercent() {
        Cashback primary = resolvePrimaryCashback();
        if (primary == null || primary.getPercent() == null || primary.getPercent() <= 0) {
            return null;
        }
        return primary.getPercent();
    }

    /**
     * Основной промо-бонус: неистёкший с максимальной суммой.
     */
    public Cashback resolvePrimaryCashback() {
        if (cashbacks == null || cashbacks.isEmpty()) {
            return null;
        }
        Cashback best = null;
        int bestSum = -1;
        for (Cashback cashback : cashbacks) {
            if (!isUsable(cashback) || cashback.getSum() == null) {
                continue;
            }
            if (cashback.getSum() > bestSum) {
                bestSum = cashback.getSum();
                best = cashback;
            }
        }
        return best;
    }

    private static boolean isUsable(Cashback cashback) {
        if (cashback == null || cashback.getSum() == null || cashback.getSum() <= 0) {
            return false;
        }
        if (cashback.getExpirationDate() == null || cashback.getExpirationDate().isBlank()) {
            return true;
        }
        try {
            return Instant.parse(cashback.getExpirationDate()).isAfter(Instant.now());
        } catch (Exception e) {
            return true;
        }
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
