package ru.oparin.solution.service.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.WbProductCard;
import ru.oparin.solution.repository.WbProductCardRepository;
import ru.oparin.solution.service.CabinetScopeStatusService;
import ru.oparin.solution.service.events.WbApiEventService;
import ru.oparin.solution.service.events.payload.WbItemRatingSyncStepPayload;
import ru.oparin.solution.service.wb.WbApiCategory;
import ru.oparin.solution.service.wb.WbPublicCatalogClient;
import ru.oparin.solution.util.ArticleRatingUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Синхронизация рейтинга карточек из публичного catalog API WB ({@code reviewRating} —
 * те же звёзды, что на странице артикула). Запускается теми же событиями, что и обновление кабинета.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WbItemRatingSyncService {

    /** Размер батча nmId на один шаг события. */
    private static final int BATCH_SIZE = 100;

    private final WbPublicCatalogClient publicCatalogClient;
    private final WbProductCardRepository productCardRepository;
    private final CabinetScopeStatusService cabinetScopeStatusService;
    private final WbApiEventService wbApiEventService;

    /**
     * self-proxy, чтобы {@code @Transactional} на сохранении не обходился самовызовом.
     */
    @Lazy
    @Autowired
    private WbItemRatingSyncService self;

    public record ItemRatingStepProcessingResult(boolean completedRun) {}

    /**
     * Один шаг: батч nmId кабинета → публичный {@code reviewRating} → сохранение.
     * HTTP вне транзакции.
     */
    public ItemRatingStepProcessingResult processStepInNewTransaction(
            Cabinet cabinet,
            String apiKey,
            WbItemRatingSyncStepPayload step,
            String triggerSource
    ) {
        long cabinetId = cabinet.getId();
        log.info("Старт шага синхронизации рейтинга (public catalog): cabinetId={}, offset={}, syncStartedAt={}, triggerSource={}",
                cabinetId, step.offset(), step.syncStartedAt(), triggerSource);

        List<WbProductCard> allCards = productCardRepository.findByCabinet_Id(cabinetId);
        if (allCards.isEmpty()) {
            log.info("Нет карточек для рейтинга: cabinetId={}", cabinetId);
            self.finalizeLegacyRun(cabinetId, step.syncStartedAt());
            return new ItemRatingStepProcessingResult(true);
        }

        List<Long> orderedNmIds = allCards.stream()
                .map(WbProductCard::getNmId)
                .filter(Objects::nonNull)
                .sorted()
                .toList();

        int from = Math.max(0, step.offset());
        if (from >= orderedNmIds.size()) {
            self.finalizeLegacyRun(cabinetId, step.syncStartedAt());
            log.info("Синхронизация рейтинга (public catalog) завершена: cabinetId={}", cabinetId);
            return new ItemRatingStepProcessingResult(true);
        }

        int to = Math.min(from + BATCH_SIZE, orderedNmIds.size());
        List<Long> batchNmIds = orderedNmIds.subList(from, to);
        Map<Long, Double> ratings = publicCatalogClient.fetchReviewRatings(batchNmIds);
        log.info("Батч reviewRating получен: cabinetId={}, offset={}, batchSize={}, ratingsFound={}",
                cabinetId, from, batchNmIds.size(), ratings.size());

        self.persistPublicBatch(cabinetId, batchNmIds, ratings, step.syncStartedAt());

        if (to < orderedNmIds.size()) {
            wbApiEventService.enqueueNextItemRatingStepEvent(
                    cabinetId,
                    WbItemRatingSyncStepPayload.builder()
                            .offset(to)
                            .syncStartedAt(step.syncStartedAt())
                            .dateFrom(step.dateFrom())
                            .dateTo(step.dateTo())
                            .includeStocks(step.includeStocks())
                            .build(),
                    triggerSource
            );
            return new ItemRatingStepProcessingResult(false);
        }

        self.finalizeLegacyRun(cabinetId, step.syncStartedAt());
        log.info("Синхронизация рейтинга (public catalog) завершена: cabinetId={}", cabinetId);
        return new ItemRatingStepProcessingResult(true);
    }

    /**
     * Legacy: полный прогон в одном потоке (все батчи подряд).
     */
    public void syncForCabinetInNewTransaction(Cabinet cabinet, String apiKey) {
        syncForCabinet(cabinet, apiKey);
    }

    public void syncForCabinet(Cabinet cabinet, String apiKey) {
        long cabinetId = cabinet.getId();
        List<WbProductCard> allCards = productCardRepository.findByCabinet_Id(cabinetId);
        if (allCards.isEmpty()) {
            return;
        }

        LocalDateTime syncStartedAt = LocalDateTime.now();
        List<Long> orderedNmIds = allCards.stream()
                .map(WbProductCard::getNmId)
                .filter(Objects::nonNull)
                .sorted()
                .toList();

        for (int from = 0; from < orderedNmIds.size(); from += BATCH_SIZE) {
            int to = Math.min(from + BATCH_SIZE, orderedNmIds.size());
            List<Long> batch = orderedNmIds.subList(from, to);
            Map<Long, Double> ratings = publicCatalogClient.fetchReviewRatings(batch);
            self.persistPublicBatch(cabinetId, batch, ratings, syncStartedAt);
            if (to < orderedNmIds.size()) {
                sleepQuietly(500L);
            }
        }
        self.finalizeLegacyRun(cabinetId, syncStartedAt);
        log.info("Legacy-синхронизация рейтинга (public catalog) завершена: cabinetId={}", cabinetId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistPublicBatch(
            Long cabinetId,
            List<Long> batchNmIds,
            Map<Long, Double> ratingsByNmId,
            LocalDateTime syncStartedAt
    ) {
        if (batchNmIds == null || batchNmIds.isEmpty()) {
            return;
        }
        Map<Long, Double> safeRatings = ratingsByNmId != null ? ratingsByNmId : Map.of();
        List<WbProductCard> existing = productCardRepository.findByCabinet_Id(cabinetId).stream()
                .filter(c -> c.getNmId() != null && batchNmIds.contains(c.getNmId()))
                .toList();

        Map<Long, BigDecimal> normalized = new HashMap<>();
        for (Long nmId : batchNmIds) {
            BigDecimal star = toStarRating(safeRatings.get(nmId));
            if (star != null) {
                normalized.put(nmId, star);
            }
        }

        for (WbProductCard productCard : existing) {
            productCard.setRatingSyncedAt(syncStartedAt);
            BigDecimal fromWb = normalized.get(productCard.getNmId());
            productCard.setRating(ArticleRatingUtils.resolveRatingAfterSync(productCard.getRating(), fromWb));
        }
        if (!existing.isEmpty()) {
            productCardRepository.saveAll(existing);
            log.info("Обновлён рейтинг (public catalog): cabinetId={}, cardsUpdated={}, withRating={}",
                    cabinetId, existing.size(), normalized.size());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeLegacyRun(Long cabinetId, LocalDateTime syncStartedAt) {
        finalizeRatings(cabinetId, syncStartedAt);
        cabinetScopeStatusService.recordSuccess(cabinetId, WbApiCategory.ANALYTICS);
    }

    private void finalizeRatings(Long cabinetId, LocalDateTime syncStartedAt) {
        // Не обнуляем рейтинг у карточек вне батча: публичный API мог временно не вернуть товар.
        int cardsTotal = productCardRepository.findByCabinet_Id(cabinetId).size();
        log.info("Финализация рейтинга (public catalog): cabinetId={}, cardsTotal={}, syncStartedAt={}",
                cabinetId, cardsTotal, syncStartedAt);
    }

    private static BigDecimal toStarRating(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return null;
        }
        if (value <= 0 || value > 5.0d) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Прервана пауза между батчами публичного catalog API", e);
        }
    }
}
