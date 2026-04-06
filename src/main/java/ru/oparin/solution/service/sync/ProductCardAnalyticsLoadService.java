package ru.oparin.solution.service.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.wb.SaleFunnelResponse;
import ru.oparin.solution.model.ProductCard;
import ru.oparin.solution.model.ProductCardAnalytics;
import ru.oparin.solution.repository.ProductCardAnalyticsRepository;
import ru.oparin.solution.service.wb.WbAnalyticsApiClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Загрузка и сохранение аналитики воронки продаж по карточкам товаров из WB API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductCardAnalyticsLoadService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final ProductCardAnalyticsRepository analyticsRepository;
    private final WbAnalyticsApiClient analyticsApiClient;

    /**
     * Загружает аналитику за период для всех карточек.
     *
     * @return количество успешных загрузок и ошибок
     */
    public ProcessingResult loadAnalyticsForAllCards(
            List<ProductCard> cards,
            String apiKey,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        int successCount = 0;
        int errorCount = 0;

        for (ProductCard card : cards) {
            try {
                loadAnalyticsForCard(card, apiKey, dateFrom, dateTo);
                successCount++;
            } catch (Exception e) {
                log.error("Ошибка при загрузке аналитики для карточки nmID {}: {}", card.getNmId(), e.getMessage());
                errorCount++;
            }
        }

        return new ProcessingResult(successCount, errorCount);
    }

    public void loadAnalyticsForCard(ProductCard card, String apiKey, LocalDate dateFrom, LocalDate dateTo) {
        SaleFunnelResponse analyticsResponse = fetchAnalytics(apiKey, card.getNmId(), dateFrom, dateTo);

        if (analyticsResponse == null || analyticsResponse.getData() == null) {
            log.warn("Аналитика для карточки nmID {} не получена", card.getNmId());
            return;
        }

        saveAnalyticsData(card, analyticsResponse, dateFrom, dateTo);
    }

    private SaleFunnelResponse fetchAnalytics(String apiKey, Long nmId, LocalDate dateFrom, LocalDate dateTo) {
        String dateFromStr = dateFrom.format(DATE_FORMATTER);
        String dateToStr = dateTo.format(DATE_FORMATTER);
        return analyticsApiClient.getSaleFunnelProduct(apiKey, nmId, dateFromStr, dateToStr);
    }

    private void saveAnalyticsData(
            ProductCard card,
            SaleFunnelResponse analyticsResponse,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        int savedCount = 0;
        int updatedCount = 0;
        List<AnalyticsSaveItem> itemsToSave = new ArrayList<>();

        for (SaleFunnelResponse.DailyData dailyData : analyticsResponse.getData()) {
            if (dailyData == null || dailyData.getDt() == null) continue;
            if (!card.getNmId().equals(dailyData.getNmId())) continue;

            try {
                LocalDate date = LocalDate.parse(dailyData.getDt(), DATE_FORMATTER);
                if (!isDateInRange(date, dateFrom, dateTo)) continue;
                itemsToSave.add(new AnalyticsSaveItem(card, dailyData, date));
            } catch (Exception e) {
                log.error("Ошибка при подготовке аналитики для карточки nmID {} за дату {}: {}",
                        card.getNmId(), dailyData.getDt(), e.getMessage());
            }
        }

        if (!itemsToSave.isEmpty()) {
            SaveBatchResult batchResult = saveAnalyticsBatch(itemsToSave);
            savedCount = batchResult.savedCount();
            updatedCount = batchResult.updatedCount();
        }

        log.info("Аналитика для карточки nmID {}: создано {}, обновлено {}",
                card.getNmId(), savedCount, updatedCount);
    }

    @Transactional
    protected SaveBatchResult saveAnalyticsBatch(List<AnalyticsSaveItem> items) {
        int savedCount = 0;
        int updatedCount = 0;

        for (AnalyticsSaveItem item : items) {
            try {
                boolean isNew = saveOrUpdateAnalytics(item.card(), item.dailyData(), item.date());
                if (isNew) savedCount++;
                else updatedCount++;
            } catch (Exception e) {
                log.error("Ошибка при сохранении аналитики для карточки nmID {} за дату {}: {}",
                        item.card().getNmId(), item.date(), e.getMessage());
            }
        }
        return new SaveBatchResult(savedCount, updatedCount);
    }

    private boolean saveOrUpdateAnalytics(ProductCard card, SaleFunnelResponse.DailyData dailyData, LocalDate date) {
        Long cabinetId = card.getCabinet() != null ? card.getCabinet().getId() : null;
        Optional<ProductCardAnalytics> existing = cabinetId != null
                ? analyticsRepository.findByProductCardNmIdAndDateAndCabinet_Id(card.getNmId(), date, cabinetId)
                : analyticsRepository.findByProductCardNmIdAndDate(card.getNmId(), date);

        ProductCardAnalytics analytics = existing.orElseGet(() -> {
            ProductCardAnalytics newAnalytics = new ProductCardAnalytics();
            newAnalytics.setProductCard(card);
            newAnalytics.setDate(date);
            if (card.getCabinet() != null) {
                newAnalytics.setCabinet(card.getCabinet());
            }
            return newAnalytics;
        });
        if (card.getCabinet() != null && analytics.getCabinet() == null) {
            analytics.setCabinet(card.getCabinet());
        }

        analytics.setOpenCard(dailyData.getOpenCardCount());
        analytics.setAddToCart(dailyData.getAddToCartCount());
        analytics.setOrders(dailyData.getOrdersCount());
        analytics.setOrdersSum(dailyData.getOrdersSumRub());
        analyticsRepository.save(analytics);

        return existing.isEmpty();
    }

    private boolean isDateInRange(LocalDate date, LocalDate dateFrom, LocalDate dateTo) {
        return !date.isBefore(dateFrom) && !date.isAfter(dateTo);
    }

    public record ProcessingResult(int successCount, int errorCount) {}

    private record AnalyticsSaveItem(ProductCard card, SaleFunnelResponse.DailyData dailyData, LocalDate date) {}

    private record SaveBatchResult(int savedCount, int updatedCount) {}
}
