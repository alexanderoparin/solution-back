package ru.oparin.solution.service.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.ozon.OzonProductRatingBySkuResponse;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.OzonProductCard;
import ru.oparin.solution.repository.OzonProductCardRepository;
import ru.oparin.solution.service.events.OzonApiEventService;
import ru.oparin.solution.service.events.payload.OzonContentRatingCabinetPayload;
import ru.oparin.solution.service.ozon.OzonProductsApiClient;
import ru.oparin.solution.util.ArticleRatingUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Синхронизация контент-рейтинга Ozon ({@code POST /v1/product/rating-by-sku}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OzonContentRatingSyncService {

    /** Лимит SKU в одном запросе rating-by-sku. */
    public static final int BATCH_SIZE = 100;

    private final OzonProductsApiClient productsApiClient;
    private final OzonProductCardRepository productCardRepository;
    private final OzonApiEventService ozonApiEventService;

    @Lazy
    @Autowired
    private OzonContentRatingSyncService self;

    /**
     * Результат обработки одного батча.
     *
     * @param completedRun {@code true}, если все SKU кабинета обработаны
     */
    public record ContentRatingStepResult(boolean completedRun, int updatedCount) {
    }

    /**
     * HTTP вне транзакции, сохранение — в REQUIRES_NEW через self-proxy.
     */
    public ContentRatingStepResult processStep(
            Cabinet cabinet,
            String clientId,
            String apiKey,
            OzonContentRatingCabinetPayload step,
            String triggerSource
    ) {
        int offset = Math.max(0, step.offset());
        LocalDateTime syncStartedAt = step.syncStartedAt() != null ? step.syncStartedAt() : LocalDateTime.now();
        List<Long> skus = productCardRepository.findSkusByCabinetId(
                cabinet.getId(), PageRequest.of(offset / BATCH_SIZE, BATCH_SIZE));
        if (skus.isEmpty()) {
            self.finalizeRatings(cabinet.getId(), syncStartedAt);
            log.info("Ozon content-rating: нет SKU для cabinetId={}, offset={}", cabinet.getId(), offset);
            return new ContentRatingStepResult(true, 0);
        }

        OzonProductRatingBySkuResponse response = productsApiClient.getProductRatingBySku(clientId, apiKey, skus);
        int updated = self.persistBatch(cabinet.getId(), response.resolveProducts(), syncStartedAt);

        if (skus.size() >= BATCH_SIZE) {
            ozonApiEventService.enqueueNextContentRatingCabinetEvent(
                    cabinet.getId(),
                    OzonContentRatingCabinetPayload.builder()
                            .offset(offset + skus.size())
                            .syncStartedAt(syncStartedAt)
                            .build(),
                    triggerSource
            );
            return new ContentRatingStepResult(false, updated);
        }

        self.finalizeRatings(cabinet.getId(), syncStartedAt);
        log.info("Ozon content-rating завершён: cabinetId={}, lastBatchUpdated={}", cabinet.getId(), updated);
        return new ContentRatingStepResult(true, updated);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int persistBatch(
            Long cabinetId,
            List<OzonProductRatingBySkuResponse.Product> products,
            LocalDateTime syncedAt
    ) {
        if (products == null || products.isEmpty()) {
            return 0;
        }
        Map<Long, BigDecimal> ratingBySku = new HashMap<>();
        for (OzonProductRatingBySkuResponse.Product product : products) {
            if (product.getSku() == null) {
                continue;
            }
            ratingBySku.put(product.getSku(), product.getRating());
        }
        if (ratingBySku.isEmpty()) {
            return 0;
        }
        List<OzonProductCard> cards = productCardRepository.findByCabinet_IdAndSkuIn(cabinetId, ratingBySku.keySet());
        int updated = 0;
        for (OzonProductCard card : cards) {
            BigDecimal fromApi = ratingBySku.get(card.getSku());
            card.setContentRating(ArticleRatingUtils.resolveRatingAfterSync(card.getContentRating(), fromApi));
            card.setContentRatingSyncedAt(syncedAt);
            updated++;
        }
        productCardRepository.saveAll(cards);
        log.info("Ozon content-rating: cabinetId={}, обновлено карточек={}", cabinetId, updated);
        return updated;
    }

    /**
     * Сбрасывает рейтинг у карточек, не попавших в текущий прогон синка.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeRatings(Long cabinetId, LocalDateTime syncStartedAt) {
        List<OzonProductCard> cards = productCardRepository.findByCabinet_IdOrderByProductIdAsc(cabinetId);
        int cleared = 0;
        for (OzonProductCard card : cards) {
            if (card.getContentRating() == null) {
                continue;
            }
            if (card.getContentRatingSyncedAt() == null
                    || card.getContentRatingSyncedAt().isBefore(syncStartedAt)) {
                card.setContentRating(null);
                card.setContentRatingSyncedAt(syncStartedAt);
                cleared++;
            }
        }
        if (cleared > 0) {
            productCardRepository.saveAll(cards);
        }
        log.info("Ozon content-rating finalize: cabinetId={}, очищено={}", cabinetId, cleared);
    }
}
