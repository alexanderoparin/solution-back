package ru.oparin.solution.service.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.ArticleResponseDto;
import ru.oparin.solution.dto.analytics.PeriodDto;
import ru.oparin.solution.model.User;
import ru.oparin.solution.service.analytics.ozon.OzonArticleAnalyticsQuery;
import ru.oparin.solution.service.analytics.wb.WbArticleAnalyticsQuery;

import java.time.LocalDate;
import java.util.List;

/**
 * Маршрутизация карточки артикула по маркетплейсу.
 */
@Service
@RequiredArgsConstructor
public class ArticleAnalyticsQuery {

    private final AnalyticsMarketplaceRouter marketplaceRouter;
    private final WbArticleAnalyticsQuery wbArticleAnalyticsQuery;
    private final OzonArticleAnalyticsQuery ozonArticleAnalyticsQuery;

    /**
     * Детальная информация по артикулу WB или товару Ozon.
     */
    @Transactional(readOnly = true)
    public ArticleResponseDto getArticle(
            User seller,
            Long cabinetId,
            Long nmId,
            List<PeriodDto> periods,
            LocalDate campaignDateFrom,
            LocalDate campaignDateTo,
            LocalDate dailyDataDateFrom,
            LocalDate dailyDataDateTo,
            Long dailyDataCampaignAdvertId
    ) {
        if (marketplaceRouter.isOzon(cabinetId)) {
            return ozonArticleAnalyticsQuery.getArticle(
                    cabinetId,
                    nmId,
                    periods,
                    campaignDateFrom,
                    campaignDateTo,
                    dailyDataDateFrom,
                    dailyDataDateTo
            );
        }
        return wbArticleAnalyticsQuery.getArticle(
                seller,
                cabinetId,
                nmId,
                periods,
                campaignDateFrom,
                campaignDateTo,
                dailyDataDateFrom,
                dailyDataDateTo,
                dailyDataCampaignAdvertId
        );
    }
}
