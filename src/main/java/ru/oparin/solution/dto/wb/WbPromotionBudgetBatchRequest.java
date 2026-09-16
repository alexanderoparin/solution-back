package ru.oparin.solution.dto.wb;

import lombok.*;

import java.util.List;

/**
 * Тело POST /api/advert/v2/budget: от 1 до 50 ID кампаний.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbPromotionBudgetBatchRequest {

    /** ID кампаний, остатки бюджетов которых нужно получить. */
    private List<Long> advertIds;
}
