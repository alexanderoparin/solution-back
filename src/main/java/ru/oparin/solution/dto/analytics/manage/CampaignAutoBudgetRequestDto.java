package ru.oparin.solution.dto.analytics.manage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CampaignAutoBudgetRequestDto {
    private boolean enabled;
    private Integer topUpAmount;
    private Integer sourceType;
    /** Использовать промо-бонусы; null = true (совместимость). */
    private Boolean usePromoCashback;
    private Integer thresholdRub;
    private Integer maxTopUpsPerDay;
}
