package ru.oparin.solution.service.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.AnalyticsSummaryRequest;
import ru.oparin.solution.dto.analytics.MetricGroupResponseDto;
import ru.oparin.solution.dto.analytics.SummaryResponseDto;
import ru.oparin.solution.model.User;
import ru.oparin.solution.service.analytics.ozon.OzonAnalyticsSummaryQuery;
import ru.oparin.solution.service.analytics.wb.WbAnalyticsSummaryQuery;

/**
 * Маршрутизация сводной аналитики по маркетплейсу.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsSummaryQuery {

    private final AnalyticsMarketplaceRouter marketplaceRouter;
    private final WbAnalyticsSummaryQuery wbAnalyticsSummaryQuery;
    private final OzonAnalyticsSummaryQuery ozonAnalyticsSummaryQuery;

    /**
     * Сводная аналитика кабинета/продавца.
     */
    @Transactional(readOnly = true)
    public SummaryResponseDto getSummary(User seller, Long cabinetId, AnalyticsSummaryRequest request) {
        AnalyticsPercentChange.validatePeriods(request.getPeriods());
        if (marketplaceRouter.isOzon(cabinetId)) {
            return ozonAnalyticsSummaryQuery.getSummary(cabinetId, request);
        }
        return wbAnalyticsSummaryQuery.getSummary(seller, cabinetId, request);
    }

    /**
     * Детальные значения одной метрики по артикулам.
     */
    @Transactional(readOnly = true)
    public MetricGroupResponseDto getMetricGroup(
            User seller,
            Long cabinetId,
            String metricName,
            AnalyticsSummaryRequest request
    ) {
        if (marketplaceRouter.isOzon(cabinetId)) {
            return ozonAnalyticsSummaryQuery.getMetricGroup(cabinetId, metricName, request);
        }
        return wbAnalyticsSummaryQuery.getMetricGroup(seller, cabinetId, metricName, request);
    }
}
