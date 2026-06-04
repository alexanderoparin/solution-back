package ru.oparin.solution.dto.analytics.manage;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignScheduleSlotDto {
    private Long id;
    private short dayOfWeek;
    private String startTime;
    private String endTime;
    private Integer budgetRub;
    private UUID repeatGroupId;
    private String repeatMode;
}
