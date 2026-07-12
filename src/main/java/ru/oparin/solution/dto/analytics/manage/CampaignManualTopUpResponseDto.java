package ru.oparin.solution.dto.analytics.manage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Результат единоразового пополнения бюджета РК.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignManualTopUpResponseDto {

    private int topUpAmount;
    private int budgetAfterTopUp;
    private String message;
}
