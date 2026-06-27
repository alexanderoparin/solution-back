package ru.oparin.solution.service.campaign;

import java.time.LocalDateTime;

/**
 * Временное окно графика бюджета РК.
 */
public record CampaignBudgetChartPeriod(LocalDateTime from, LocalDateTime to) {
}
