package ru.oparin.solution.dto.analytics.manage;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceSourceOptionDto {
    private int type;
    private String label;
    /** Доступно для этого источника, ₽ (для Бонусов — поле bonus WB). */
    private Integer availableRub;
    /**
     * Промо-бонусы (cashbacks), ₽.
     * Списываются вместе с type 0/1 (не с type 3), до {@link #cashbackPercent}% от суммы пополнения.
     */
    private Integer cashbackRub;
    /** Лимит доли промо от суммы пополнения (например 50). Для type 0/1. */
    private Integer cashbackPercent;
}
