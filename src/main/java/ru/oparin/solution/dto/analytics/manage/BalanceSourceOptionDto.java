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
    /** Промо-бонусы (cashbacks), ₽ — только для type=3, справочно. */
    private Integer cashbackRub;
}
