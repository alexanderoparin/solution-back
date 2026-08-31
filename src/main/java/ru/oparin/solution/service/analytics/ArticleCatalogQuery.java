package ru.oparin.solution.service.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.ArticleSummaryDto;
import ru.oparin.solution.model.User;
import ru.oparin.solution.model.WbProductCard;
import ru.oparin.solution.service.analytics.ozon.OzonArticleCatalogQuery;
import ru.oparin.solution.service.analytics.wb.WbArticleCatalogQuery;

import java.util.List;

/**
 * Маршрутизация каталога артикулов по маркетплейсу кабинета.
 */
@Service
@RequiredArgsConstructor
public class ArticleCatalogQuery {

    private final AnalyticsMarketplaceRouter marketplaceRouter;
    private final WbArticleCatalogQuery wbArticleCatalogQuery;
    private final OzonArticleCatalogQuery ozonArticleCatalogQuery;

    /**
     * Список артикулов кабинета/продавца для попапа фильтра.
     */
    @Transactional(readOnly = true)
    public List<ArticleSummaryDto> getArticleList(
            User seller,
            Long cabinetId,
            Boolean onlyWithPhoto,
            Boolean onlyPriority,
            Boolean onlyInAdvertising
    ) {
        if (marketplaceRouter.isOzon(cabinetId)) {
            return ozonArticleCatalogQuery.getArticleList(cabinetId, onlyWithPhoto, onlyInAdvertising);
        }
        return wbArticleCatalogQuery.getArticleList(
                seller, cabinetId, onlyWithPhoto, onlyPriority, onlyInAdvertising);
    }

    /**
     * Карточка WB с проверкой владельца.
     */
    @Transactional(readOnly = true)
    public WbProductCard findCardBySeller(Long nmId, Long sellerId, Long cabinetId) {
        return wbArticleCatalogQuery.findCardBySeller(nmId, sellerId, cabinetId);
    }

    /**
     * Сохраняет флаг приоритета артикула WB.
     */
    @Transactional
    public void updateArticlePriority(User seller, Long cabinetId, Long nmId, boolean isPriority) {
        wbArticleCatalogQuery.updateArticlePriority(seller, cabinetId, nmId, isPriority);
    }
}
