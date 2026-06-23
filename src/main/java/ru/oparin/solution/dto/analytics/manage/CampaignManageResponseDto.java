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
    /** OFF, WAITING, RUNNING, SLOT_LIMIT, NO_ACCESS, NO_SLOTS — статус автоматики биддера. */
    private String bidderStatus;
    /** Автозапуск по расписанию включён (не нажата «Остановить»). */
    private boolean scheduleEnabled;
    private CampaignAutoBudgetDto autoBudget;
    private List<CampaignScheduleSlotDto> slots;
}
