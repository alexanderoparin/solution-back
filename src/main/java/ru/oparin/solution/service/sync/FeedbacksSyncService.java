package ru.oparin.solution.service.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import ru.oparin.solution.dto.wb.FeedbackItem;
import ru.oparin.solution.dto.wb.FeedbacksResponse;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.FeedbacksSyncAccumulatorRepository;
import ru.oparin.solution.repository.FeedbacksSyncPageCheckpointRepository;
import ru.oparin.solution.repository.FeedbacksSyncRunRepository;
import ru.oparin.solution.repository.ProductCardRepository;
import ru.oparin.solution.service.CabinetScopeStatusService;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.events.WbApiEventService;
import ru.oparin.solution.service.events.payload.FeedbacksSyncStepPayload;
import ru.oparin.solution.service.wb.WbApiCategory;
import ru.oparin.solution.service.wb.WbFeedbacksApiClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Синхронизация рейтинга и количества отзывов по товарам из API отзывов WB.
 * Запрашивает и обработанные (isAnswered=true), и необработанные (isAnswered=false) отзывы,
 * объединяет по nmId и считает общий рейтинг и количество отзывов.
 * Пагинация в рамках одного вызова: пауза между полными страницами зависит от типа токена.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbacksSyncService {

    private static final int PAGE_SIZE = 5000;

    private final WbFeedbacksApiClient feedbacksApiClient;
    private final ProductCardRepository productCardRepository;
    private final CabinetService cabinetService;
    private final CabinetScopeStatusService cabinetScopeStatusService;
    private final FeedbacksSyncRunRepository feedbacksSyncRunRepository;
    private final FeedbacksSyncAccumulatorRepository feedbacksSyncAccumulatorRepository;
    private final FeedbacksSyncPageCheckpointRepository feedbacksSyncPageCheckpointRepository;
    private final WbApiEventService wbApiEventService;

    public record FeedbacksStepProcessingResult(boolean completedRun) {}

    /**
     * Обрабатывает один шаг (одну страницу) run-пайплайна отзывов.
     * Идемпотентность страницы обеспечивается checkpoint (runId + isAnswered + skip).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FeedbacksStepProcessingResult processFeedbacksStepInNewTransaction(
            Cabinet cabinet,
            String apiKey,
            FeedbacksSyncStepPayload step,
            String triggerSource
    ) {
        FeedbacksSyncRun run = feedbacksSyncRunRepository.findById(step.runId())
                .orElseThrow(() -> new IllegalArgumentException("Feedbacks run не найден: " + step.runId()));
        if (run.getStatus() != FeedbacksSyncRunStatus.RUNNING) {
            return new FeedbacksStepProcessingResult(run.getStatus() == FeedbacksSyncRunStatus.COMPLETED);
        }
        if (feedbacksSyncPageCheckpointRepository.existsByRunIdAndIsAnsweredAndSkipValue(
                step.runId(), step.isAnswered(), step.skip())) {
            return new FeedbacksStepProcessingResult(false);
        }

        FeedbacksResponse response = feedbacksApiClient.getFeedbacks(apiKey, step.isAnswered(), null, PAGE_SIZE, step.skip());
        List<FeedbackItem> feedbacks = response.getData() != null ? response.getData().getFeedbacks() : null;
        List<FeedbackItem> page = (feedbacks == null) ? List.of() : feedbacks;

        feedbacksSyncPageCheckpointRepository.save(FeedbacksSyncPageCheckpoint.builder()
                .runId(step.runId())
                .isAnswered(step.isAnswered())
                .skipValue(step.skip())
                .createdAt(LocalDateTime.now())
                .build());

        mergePageIntoAccumulator(step.runId(), page);

        boolean hasMore = page.size() >= PAGE_SIZE;
        if (hasMore) {
            wbApiEventService.enqueueNextFeedbacksSyncStepEvent(
                    cabinet.getId(),
                    FeedbacksSyncStepPayload.builder()
                            .runId(step.runId())
                            .isAnswered(step.isAnswered())
                            .skip(step.skip() + page.size())
                            .dateFrom(step.dateFrom())
                            .dateTo(step.dateTo())
                            .includeStocks(step.includeStocks())
                            .build(),
                    triggerSource,
                    run.getTokenTypeSnapshot()
            );
            run.setUpdatedAt(LocalDateTime.now());
            feedbacksSyncRunRepository.save(run);
            return new FeedbacksStepProcessingResult(false);
        }

        if (step.isAnswered()) {
            wbApiEventService.enqueueNextFeedbacksSyncStepEvent(
                    cabinet.getId(),
                    FeedbacksSyncStepPayload.builder()
                            .runId(step.runId())
                            .isAnswered(false)
                            .skip(0)
                            .dateFrom(step.dateFrom())
                            .dateTo(step.dateTo())
                            .includeStocks(step.includeStocks())
                            .build(),
                    triggerSource,
                    run.getTokenTypeSnapshot()
            );
            run.setUpdatedAt(LocalDateTime.now());
            feedbacksSyncRunRepository.save(run);
            return new FeedbacksStepProcessingResult(false);
        }

        applyAccumulatorToProductCards(cabinet.getId(), step.runId());
        cabinetScopeStatusService.recordSuccess(cabinet.getId(), WbApiCategory.FEEDBACKS_AND_QUESTIONS);
        feedbacksSyncAccumulatorRepository.deleteById_RunId(step.runId());
        feedbacksSyncPageCheckpointRepository.deleteByRunId(step.runId());
        run.setStatus(FeedbacksSyncRunStatus.COMPLETED);
        run.setLastError(null);
        run.setUpdatedAt(LocalDateTime.now());
        run.setFinishedAt(LocalDateTime.now());
        feedbacksSyncRunRepository.save(run);
        return new FeedbacksStepProcessingResult(true);
    }

    /**
     * Синхронизирует отзывы в отдельной транзакции. Вызывать из полного обновления кабинета,
     * чтобы 401 (нет доступа к категории «Вопросы и отзывы») не помечал основную транзакцию как rollback-only.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncFeedbacksForCabinetInNewTransaction(Cabinet cabinet, String apiKey) {
        syncFeedbacksForCabinet(cabinet, apiKey);
    }

    /**
     * Синхронизирует рейтинг и количество отзывов для всех карточек кабинета.
     * Использует тот же API-ключ, что и остальные запросы (нужна категория «Вопросы и отзывы»).
     */
    @Transactional
    public void syncFeedbacksForCabinet(Cabinet cabinet, String apiKey) {
        long cabinetId = cabinet.getId();
        Map<Long, RatingCount> byNmId = fetchAndAggregateFeedbacks(apiKey, true);
        feedbacksApiClient.delayBetweenRequests(apiKey);
        Map<Long, RatingCount> unanswered = fetchAndAggregateFeedbacks(apiKey, false);
        mergeInto(byNmId, unanswered);

        List<ProductCard> cards = productCardRepository.findByCabinet_Id(cabinetId);
        for (ProductCard card : cards) {
            Long nmId = card.getNmId();
            RatingCount rc = byNmId.get(nmId);
            if (rc == null) {
                card.setRating(null);
                card.setReviewsCount(0);
            } else {
                card.setRating(rc.avg());
                card.setReviewsCount(rc.count);
            }
        }
        if (!cards.isEmpty()) {
            productCardRepository.saveAll(cards);
            int updatedWithData = (int) cards.stream().filter(c -> byNmId.containsKey(c.getNmId())).count();
            log.info("Обновлены рейтинг и отзывы для кабинета {}: обновлено карточек {}, из них с отзывами {}; в ответе API по продавцу встретилось nmId {}",
                    cabinetId, cards.size(), updatedWithData, byNmId.size());
        }
        cabinetScopeStatusService.recordSuccess(cabinetId, WbApiCategory.FEEDBACKS_AND_QUESTIONS);
    }

    /**
     * Синхронизирует рейтинг и отзывы для всех кабинетов продавцов с API-ключом.
     * Вызывается по эндпоинту (ADMIN) и после ночной загрузки аналитики.
     */
    public void syncFeedbacksForAllCabinets() {
        List<Cabinet> cabinets = cabinetService.findCabinetsWithApiKeyAndUser(Role.SELLER);
        log.info("Запуск синхронизации отзывов для {} кабинетов", cabinets.size());
        for (Cabinet cabinet : cabinets) {
            String key = cabinet.getApiKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            try {
                syncFeedbacksForCabinet(cabinet, key);
            } catch (Exception e) {
                if (is401Unauthorized(e)) {
                    log.warn("Кабинет {}: ключ не привязан к категории «Вопросы и отзывы», доступ к отзывам недоступен", cabinet.getId());
                } else {
                    log.error("Ошибка синхронизации отзывов для кабинета {}: {}", cabinet.getId(), e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Выгружает все отзывы с заданным флагом isAnswered и агрегирует по nmId (сумма оценок и количество).
     * Между полными страницами — пауза в потоке (одно событие WB API до конца выгрузки).
     */
    private Map<Long, RatingCount> fetchAndAggregateFeedbacks(String apiKey, boolean isAnswered) {
        Map<Long, RatingCount> byNmId = new HashMap<>();
        int skip = 0;
        boolean hasMore = true;
        while (hasMore) {
            FeedbacksResponse response = feedbacksApiClient.getFeedbacks(apiKey, isAnswered, null, PAGE_SIZE, skip);
            List<FeedbackItem> feedbacks = response.getData() != null ? response.getData().getFeedbacks() : null;
            if (feedbacks == null || feedbacks.isEmpty()) {
                hasMore = false;
                break;
            }
            for (FeedbackItem fb : feedbacks) {
                if (fb.getProductValuation() == null || fb.getProductDetails() == null || fb.getProductDetails().getNmId() == null) {
                    continue;
                }
                Long nmId = fb.getProductDetails().getNmId();
                byNmId.compute(nmId, (k, v) -> {
                    if (v == null) {
                        v = new RatingCount();
                    }
                    v.add(fb.getProductValuation());
                    return v;
                });
            }
            if (feedbacks.size() < PAGE_SIZE) {
                hasMore = false;
            } else {
                skip += feedbacks.size();
                feedbacksApiClient.delayBetweenRequests(apiKey);
            }
        }
        return byNmId;
    }

    private void mergeInto(Map<Long, RatingCount> target, Map<Long, RatingCount> source) {
        for (Map.Entry<Long, RatingCount> e : source.entrySet()) {
            target.compute(e.getKey(), (k, v) -> {
                if (v == null) {
                    RatingCount rc = new RatingCount();
                    rc.add(e.getValue());
                    return rc;
                }
                v.add(e.getValue());
                return v;
            });
        }
    }

    /**
     * Агрегирует страницу в staging-таблицу run.
     */
    private void mergePageIntoAccumulator(Long runId, List<FeedbackItem> page) {
        if (page == null || page.isEmpty()) {
            return;
        }
        Map<Long, RatingCount> delta = new HashMap<>();
        for (FeedbackItem fb : page) {
            if (fb.getProductValuation() == null || fb.getProductDetails() == null || fb.getProductDetails().getNmId() == null) {
                continue;
            }
            Long nmId = fb.getProductDetails().getNmId();
            delta.compute(nmId, (k, v) -> {
                if (v == null) {
                    v = new RatingCount();
                }
                v.add(fb.getProductValuation());
                return v;
            });
        }
        if (delta.isEmpty()) {
            return;
        }
        List<FeedbacksSyncAccumulator> existing = feedbacksSyncAccumulatorRepository.findByRunIdAndNmIdIn(runId, delta.keySet());
        Map<Long, FeedbacksSyncAccumulator> existingByNmId = new HashMap<>();
        for (FeedbacksSyncAccumulator acc : existing) {
            existingByNmId.put(acc.getId().getNmId(), acc);
        }

        for (Map.Entry<Long, RatingCount> entry : delta.entrySet()) {
            Long nmId = entry.getKey();
            RatingCount rc = entry.getValue();
            FeedbacksSyncAccumulator acc = existingByNmId.get(nmId);
            if (acc == null) {
                acc = FeedbacksSyncAccumulator.builder()
                        .id(new FeedbacksSyncAccumulatorId(runId, nmId))
                        .valuationSum((long) rc.sum)
                        .reviewsCount((long) rc.count)
                        .build();
            } else {
                acc.setValuationSum(acc.getValuationSum() + rc.sum);
                acc.setReviewsCount(acc.getReviewsCount() + rc.count);
            }
            feedbacksSyncAccumulatorRepository.save(acc);
        }
    }

    /**
     * Финализирует run: переносит агрегаты в product_cards.
     */
    private void applyAccumulatorToProductCards(Long cabinetId, Long runId) {
        List<FeedbacksSyncAccumulator> acc = feedbacksSyncAccumulatorRepository.findByRunId(runId);
        Map<Long, RatingCount> byNmId = new HashMap<>();
        for (FeedbacksSyncAccumulator a : acc) {
            RatingCount rc = new RatingCount();
            rc.sum = Math.toIntExact(a.getValuationSum());
            rc.count = Math.toIntExact(a.getReviewsCount());
            byNmId.put(a.getId().getNmId(), rc);
        }

        List<ProductCard> cards = productCardRepository.findByCabinet_Id(cabinetId);
        for (ProductCard card : cards) {
            RatingCount rc = byNmId.get(card.getNmId());
            if (rc == null) {
                card.setRating(null);
                card.setReviewsCount(0);
            } else {
                card.setRating(rc.avg());
                card.setReviewsCount(rc.count);
            }
        }
        if (!cards.isEmpty()) {
            productCardRepository.saveAll(cards);
        }
    }

    private static boolean is401Unauthorized(Throwable e) {
        if (e instanceof HttpClientErrorException ex) {
            HttpStatusCode status = ex.getStatusCode();
            return status != null && status.value() == 401;
        }
        return false;
    }

    private static class RatingCount {
        int sum = 0;
        int count = 0;

        void add(int valuation) {
            sum += valuation;
            count++;
        }

        void add(RatingCount other) {
            sum += other.sum;
            count += other.count;
        }

        BigDecimal avg() {
            if (count == 0) {
                return null;
            }
            return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        }
    }
}
