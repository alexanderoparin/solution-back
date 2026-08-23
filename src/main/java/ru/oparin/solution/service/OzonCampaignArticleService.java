package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.OzonCampaignArticle;
import ru.oparin.solution.model.OzonProductCard;
import ru.oparin.solution.repository.OzonCampaignArticleRepository;
import ru.oparin.solution.repository.OzonProductCardRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Сохранение связей РК Ozon ↔ SKU.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OzonCampaignArticleService {

    private final OzonCampaignArticleRepository campaignArticleRepository;
    private final OzonProductCardRepository productCardRepository;

    /**
     * Полная перезапись SKU кампании и сопоставление с product_id каталога.
     *
     * @return число сохранённых связей
     */
    @Transactional
    public int replaceCampaignSkus(Long cabinetId, Long campaignId, List<Long> skus) {
        campaignArticleRepository.deleteByCampaignId(campaignId);
        if (skus == null || skus.isEmpty()) {
            return 0;
        }
        List<Long> distinctSkus = skus.stream().filter(s -> s != null && s > 0).distinct().toList();
        if (distinctSkus.isEmpty()) {
            return 0;
        }
        Map<Long, Long> productIdBySku = productCardRepository.findByCabinet_IdAndSkuIn(cabinetId, distinctSkus).stream()
                .filter(c -> c.getSku() != null)
                .collect(Collectors.toMap(OzonProductCard::getSku, OzonProductCard::getProductId, (a, b) -> a));

        LocalDateTime now = LocalDateTime.now();
        int saved = 0;
        for (Long sku : distinctSkus) {
            OzonCampaignArticle article = new OzonCampaignArticle();
            article.setCampaignId(campaignId);
            article.setSku(sku);
            article.setProductId(productIdBySku.get(sku));
            article.setCreatedAt(now);
            article.setUpdatedAt(now);
            campaignArticleRepository.save(article);
            saved++;
        }
        log.info("Ozon campaign articles: campaignId={}, сохранено {} SKU (из них с product_id={})",
                campaignId, saved, productIdBySku.size());
        return saved;
    }

    /**
     * Карта campaignId → число SKU.
     */
    @Transactional(readOnly = true)
    public Map<Long, Integer> countArticlesByCampaignIds(List<Long> campaignIds) {
        if (campaignIds == null || campaignIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> result = new HashMap<>();
        for (Object[] row : campaignArticleRepository.countByCampaignIdIn(campaignIds)) {
            result.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return result;
    }
}
