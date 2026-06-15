package ru.oparin.solution.service.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.wb.ItemRatingCard;
import ru.oparin.solution.dto.wb.ItemRatingResponse;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.CabinetTokenType;
import ru.oparin.solution.model.ProductCard;
import ru.oparin.solution.repository.ProductCardRepository;
import ru.oparin.solution.service.CabinetScopeStatusService;
import ru.oparin.solution.service.events.WbApiEventService;
import ru.oparin.solution.service.events.payload.ItemRatingSyncStepPayload;
import ru.oparin.solution.service.wb.WbAnalyticsApiClient;
import ru.oparin.solution.service.wb.WbApiCategory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Синхронизация рейтинга карточек из отчёта WB Analytics item-rating.
 * Категория токена: Аналитика.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ItemRatingSyncService {

    private static final int PAGE_LIMIT = 1000;

    private final WbAnalyticsApiClient analyticsApiClient;
    private final ProductCardRepository productCardRepository;
    private final CabinetScopeStatusService cabinetScopeStatusService;
    private final WbApiEventService wbApiEventService;

    public record ItemRatingStepProcessingResult(boolean completedRun) {}

    /**
     * Обрабатывает один шаг (одну страницу) синхронизации рейтинга.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ItemRatingStepProcessingResult processStepInNewTransaction(
            Cabinet cabinet,
            String apiKey,
            ItemRatingSyncStepPayload step,
            String triggerSource
    ) {
        log.info("Старт шага синхронизации item-rating: cabinetId={}, offset={}, syncStartedAt={}, triggerSource={}",
                cabinet.getId(), step.offset(), step.syncStartedAt(), triggerSource);

        ItemRatingResponse response = analyticsApiClient.postItemRating(apiKey, step.offset());
        List<ItemRatingCard> cards = response.getData() != null ? response.getData().getCards() : null;
        List<ItemRatingCard> page = cards == null ? List.of() : cards;
        log.info("Страница item-rating получена: cabinetId={}, offset={}, pageSize={}",
                cabinet.getId(), step.offset(), page.size());

        applyPageToProductCards(cabinet.getId(), page, step.syncStartedAt());

        if (page.size() >= PAGE_LIMIT) {
            wbApiEventService.enqueueNextItemRatingStepEvent(
                    cabinet.getId(),
                    ItemRatingSyncStepPayload.builder()
                            .offset(step.offset() + page.size())
                            .syncStartedAt(step.syncStartedAt())
                            .dateFrom(step.dateFrom())
                            .dateTo(step.dateTo())
                            .includeStocks(step.includeStocks())
                            .build(),
                    triggerSource
            );
            return new ItemRatingStepProcessingResult(false);
        }

        finalizeRatings(cabinet.getId(), step.syncStartedAt());
        cabinetScopeStatusService.recordSuccess(cabinet.getId(), WbApiCategory.ANALYTICS);
        log.info("Синхронизация item-rating завершена: cabinetId={}", cabinet.getId());
        return new ItemRatingStepProcessingResult(true);
    }

    /**
     * Legacy-синхронизация в одном потоке (все страницы подряд с паузой).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncForCabinetInNewTransaction(Cabinet cabinet, String apiKey) {
        syncForCabinet(cabinet, apiKey);
    }

    @Transactional
    public void syncForCabinet(Cabinet cabinet, String apiKey) {
        if (!CabinetTokenType.effective(cabinet.getTokenType()).supportsItemRating()) {
            log.info("Legacy-синхронизация item-rating пропущена для кабинета {}: базовый токен WB", cabinet.getId());
            return;
        }
        long cabinetId = cabinet.getId();
        LocalDateTime syncStartedAt = LocalDateTime.now();
        int offset = 0;
        boolean hasMore = true;

        while (hasMore) {
            ItemRatingResponse response = analyticsApiClient.postItemRating(apiKey, offset);
            List<ItemRatingCard> cards = response.getData() != null ? response.getData().getCards() : null;
            List<ItemRatingCard> page = cards == null ? List.of() : cards;
            applyPageToProductCards(cabinetId, page, syncStartedAt);

            if (page.size() < PAGE_LIMIT) {
                hasMore = false;
            } else {
                offset += page.size();
                analyticsApiClient.delayBetweenItemRatingRequests(apiKey);
            }
        }

        finalizeRatings(cabinetId, syncStartedAt);
        cabinetScopeStatusService.recordSuccess(cabinetId, WbApiCategory.ANALYTICS);
        log.info("Legacy-синхронизация item-rating завершена: cabinetId={}", cabinetId);
    }

    private void applyPageToProductCards(Long cabinetId, List<ItemRatingCard> page, LocalDateTime syncStartedAt) {
        if (page.isEmpty()) {
            return;
        }

        Map<Long, BigDecimal> ratingByNmId = new HashMap<>();
        for (ItemRatingCard card : page) {
            if (card.getNmId() == null || card.getFeedbackRating() == null || card.getFeedbackRating().getCurrent() == null) {
                continue;
            }
            ratingByNmId.put(card.getNmId(), toRating(card.getFeedbackRating().getCurrent()));
        }
        if (ratingByNmId.isEmpty()) {
            return;
        }

        List<ProductCard> existing = productCardRepository.findByCabinet_Id(cabinetId).stream()
                .filter(c -> ratingByNmId.containsKey(c.getNmId()))
                .toList();
        for (ProductCard productCard : existing) {
            productCard.setRating(ratingByNmId.get(productCard.getNmId()));
            productCard.setRatingSyncedAt(syncStartedAt);
        }
        if (!existing.isEmpty()) {
            productCardRepository.saveAll(existing);
            log.info("Обновлён рейтинг item-rating: cabinetId={}, cardsUpdated={}", cabinetId, existing.size());
        }
    }

    private void finalizeRatings(Long cabinetId, LocalDateTime syncStartedAt) {
        List<ProductCard> cards = productCardRepository.findByCabinet_Id(cabinetId);
        int cleared = 0;
        for (ProductCard card : cards) {
            LocalDateTime syncedAt = card.getRatingSyncedAt();
            if (syncedAt == null || syncedAt.isBefore(syncStartedAt)) {
                card.setRating(null);
                card.setRatingSyncedAt(null);
                cleared++;
            }
        }
        if (cleared > 0) {
            productCardRepository.saveAll(cards);
        }
        log.info("Финализация item-rating: cabinetId={}, cardsTotal={}, clearedWithoutRating={}",
                cabinetId, cards.size(), cleared);
    }

    private static BigDecimal toRating(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
