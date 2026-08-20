package ru.oparin.solution.dto.analytics.manage;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignAutoBudgetDto {
    private boolean enabled;
    private Integer topUpAmount;
    private Integer sourceType;
    /** Использовать промо-бонусы при автопополнении (type 0/1). */
    private boolean usePromoCashback;
    private Integer thresholdRub;
    private Integer maxTopUpsPerDay;
    private boolean locked;
}
