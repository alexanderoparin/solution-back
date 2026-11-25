package ru.oparin.solution.dto.analytics;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Три рейтинга товаров для дашборда (топ-5).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardRankingsResponseDto {

    @Builder.Default
    private List<DashboardRankingItemDto> topByOrders = new ArrayList<>();

    @Builder.Default
    private List<DashboardRankingItemDto> topGrowth = new ArrayList<>();

    @Builder.Default
    private List<DashboardRankingItemDto> topDecline = new ArrayList<>();
}
