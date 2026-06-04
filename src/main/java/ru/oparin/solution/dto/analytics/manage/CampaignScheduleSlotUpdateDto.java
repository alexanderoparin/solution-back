package ru.oparin.solution.dto.analytics.manage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CampaignScheduleSlotUpdateDto {
    private String startTime;
    private String endTime;
    private Integer budgetRub;
}
