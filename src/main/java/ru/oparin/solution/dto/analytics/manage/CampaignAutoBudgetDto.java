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
    private Integer thresholdRub;
    private Integer maxTopUpsPerDay;
    private boolean locked;
}
