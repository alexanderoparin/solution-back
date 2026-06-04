package ru.oparin.solution.dto.analytics.manage;

import lombok.*;
import ru.oparin.solution.dto.analytics.ArticleSummaryDto;

import java.util.List;

/**
 * Сводка страницы управления рекламной кампанией.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignManageResponseDto {
    private Long id;
    private String name;
    private Integer status;
    private String statusName;
    private Integer articlesCount;
    private List<ArticleSummaryDto> articles;
    /** RUNNING или STOPPED для UI. */
    private String operationalStatus;
    private CampaignAutoBudgetDto autoBudget;
    private List<CampaignScheduleSlotDto> slots;
    private List<CampaignChangeLogEntryDto> recentChangeLog;
}
