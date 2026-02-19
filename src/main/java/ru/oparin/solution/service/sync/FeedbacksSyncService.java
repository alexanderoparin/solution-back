package ru.oparin.solution.service.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import ru.oparin.solution.dto.wb.FeedbackItem;
import ru.oparin.solution.dto.wb.FeedbacksResponse;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.ProductCard;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.repository.CabinetRepository;
import ru.oparin.solution.repository.ProductCardRepository;
import ru.oparin.solution.service.wb.WbFeedbacksApiClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Синхронизация рейтинга и количества отзывов по товарам из API отзывов WB.
 * Запрашивает и обработанные (isAnswered=true), и необработанные (isAnswered=false) отзывы,
 * объединяет по nmId и считает общий рейтинг и количество отзывов.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbacksSyncService {

    private static final int PAGE_SIZE = 5000;

    private final WbFeedbacksApiClient feedbacksApiClient;
    private final ProductCardRepository productCardRepository;
    private final CabinetRepository cabinetRepository;

    /**
     * Синхронизирует рейтинг и количество отзывов для всех карточек кабинета.
     * Использует тот же API-ключ, что и остальные запросы (нужна категория «Вопросы и отзывы»).
     */
    @Transactional
    public void syncFeedbacksForCabinet(Cabinet cabinet, String apiKey) {
        long cabinetId = cabinet.getId();
        Map<Long, RatingCount> byNmId = fetchAndAggregateFeedbacks(apiKey, true);
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
            log.info("Обновлены рейтинг и отзывы для кабинета {}: карточек {}, уникальных nmId в отзывах {}",
                    cabinetId, cards.size(), byNmId.size());
        }
    }

    /**
     * Синхронизирует рейтинг и отзывы для всех кабинетов продавцов с API-ключом.
     * Вызывается по эндпоинту (ADMIN) и после ночной загрузки аналитики.
     */
    public void syncFeedbacksForAllCabinets() {
        List<Cabinet> cabinets = cabinetRepository.findCabinetsWithApiKeyAndUser(Role.SELLER);
        log.info("Запуск синхронизации отзывов для {} кабинетов", cabinets.size());
        for (Cabinet cabinet : cabinets) {
            String apiKey = cabinet.getApiKey();
            if (apiKey == null || apiKey.isBlank()) {
                continue;
            }
            try {
                syncFeedbacksForCabinet(cabinet, apiKey);
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
                    if (v == null) v = new RatingCount();
                    v.add(fb.getProductValuation());
                    return v;
                });
            }
            if (feedbacks.size() < PAGE_SIZE) {
                hasMore = false;
            } else {
                skip += feedbacks.size();
                WbFeedbacksApiClient.delayBetweenRequests();
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
            if (count == 0) return null;
            return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        }
    }
}
