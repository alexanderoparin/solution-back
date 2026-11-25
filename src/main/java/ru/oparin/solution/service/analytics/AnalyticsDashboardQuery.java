package ru.oparin.solution.service.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.*;
import ru.oparin.solution.model.User;
import ru.oparin.solution.service.analytics.wb.WbDashboardQuery;

import java.math.BigDecimal;
import java.util.List;

/**
 * Маршрутизация дашбордов: WB — полный расчёт, Ozon — пустые ответы (заглушка на фронте).
 */
@Service
@RequiredArgsConstructor
public class AnalyticsDashboardQuery {

    private final AnalyticsMarketplaceRouter marketplaceRouter;
    private final WbDashboardQuery wbDashboardQuery;

    /**
     * Дневная динамика метрики.
     */
    @Transactional(readOnly = true)
    public DashboardDailyResponseDto getDaily(
            User seller,
            Long cabinetId,
            DashboardDailyRequestDto request
    ) {
        AnalyticsPercentChange.validatePeriods(request.getPeriods());
        if (marketplaceRouter.isOzon(cabinetId)) {
            return DashboardDailyResponseDto.builder()
                    .metric(request.getMetric())
                    .points(List.of())
                    .build();
        }
        return wbDashboardQuery.getDaily(seller, cabinetId, request);
    }

    /**
     * Срез остатков FBO/FBS.
     */
    @Transactional(readOnly = true)
    public DashboardStocksResponseDto getStocks(
            User seller,
            Long cabinetId,
            DashboardStocksRequestDto request
    ) {
        if (marketplaceRouter.isOzon(cabinetId)) {
            return DashboardStocksResponseDto.builder()
                    .total(0)
                    .fbo(0)
                    .fbs(0)
                    .fboSharePercent(BigDecimal.ZERO)
                    .fbsSharePercent(BigDecimal.ZERO)
                    .bySize(List.of())
                    .updatedAt(null)
                    .articles(List.of())
                    .build();
        }
        return wbDashboardQuery.getStocks(seller, cabinetId, request);
    }

    /**
     * Товарные рейтинги (топ-5).
     */
    @Transactional(readOnly = true)
    public DashboardRankingsResponseDto getRankings(
            User seller,
            Long cabinetId,
            DashboardRankingsRequestDto request
    ) {
        AnalyticsPercentChange.validatePeriods(request.getPeriods());
        if (marketplaceRouter.isOzon(cabinetId)) {
            return DashboardRankingsResponseDto.builder()
                    .topByOrders(List.of())
                    .topGrowth(List.of())
                    .topDecline(List.of())
                    .build();
        }
        return wbDashboardQuery.getRankings(seller, cabinetId, request);
    }
}
