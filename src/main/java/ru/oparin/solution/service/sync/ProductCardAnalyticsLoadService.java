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
import java.time.temporal.ChronoUnit;
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
    private static final int API_CALL_DELAY_MS = 20000;

    private final ProductCardAnalyticsRepository analyticsRepository;
    private final WbAnalyticsApiClient analyticsApiClient;

    /**
     * Загружает аналитику за период для всех карточек (с задержкой между запросами к WB).
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

    private void loadAnalyticsForCard(ProductCard card, String apiKey, LocalDate dateFrom, LocalDate dateTo) {
        Long cabinetId = card.getCabinet() != null ? card.getCabinet().getId() : null;
        List<LocalDate> existingDates = getExistingAnalyticsDates(card.getNmId(), cabinetId, dateFrom, dateTo);

        if (isAllDatesPresent(existingDates, dateFrom, dateTo)) {
            log.info("Аналитика для nmID {} за период {} - {} уже в БД", card.getNmId(), dateFrom, dateTo);
            return;
        }

        DateRange requestRange = calculateRequestRange(existingDates, dateFrom, dateTo);
        if (requestRange == null) {
            return;
        }

        if (!existingDates.isEmpty()) {
            long totalDays = ChronoUnit.DAYS.between(dateFrom, dateTo) + 1;
            log.info("Для nmID {} уже есть аналитика за {} из {} дней. Запрашиваем период {} - {}",
                    card.getNmId(), existingDates.size(), totalDays, requestRange.from(), requestRange.to());
        }

        SaleFunnelResponse analyticsResponse = fetchAnalytics(apiKey, card.getNmId(), requestRange.from(), requestRange.to());

        if (analyticsResponse == null || analyticsResponse.getData() == null) {
            log.warn("Аналитика для карточки nmID {} не получена", card.getNmId());
            return;
        }

        saveAnalyticsData(card, analyticsResponse, existingDates, dateFrom, dateTo);
    }

    private SaleFunnelResponse fetchAnalytics(String apiKey, Long nmId, LocalDate dateFrom, LocalDate dateTo) {
        SyncDelayUtil.sleep(API_CALL_DELAY_MS);
        String dateFromStr = dateFrom.format(DATE_FORMATTER);
        String dateToStr = dateTo.format(DATE_FORMATTER);
        return analyticsApiClient.getSaleFunnelProduct(apiKey, nmId, dateFromStr, dateToStr);
    }

    private void saveAnalyticsData(
            ProductCard card,
            SaleFunnelResponse analyticsResponse,
            List<LocalDate> existingDates,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        int savedCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        List<AnalyticsSaveItem> itemsToSave = new ArrayList<>();

        for (SaleFunnelResponse.DailyData dailyData : analyticsResponse.getData()) {
            if (dailyData == null || dailyData.getDt() == null) continue;
            if (!card.getNmId().equals(dailyData.getNmId())) continue;

            try {
                LocalDate date = LocalDate.parse(dailyData.getDt(), DATE_FORMATTER);
                if (!isDateInRange(date, dateFrom, dateTo)) continue;
                if (existingDates.contains(date)) {
                    skippedCount++;
                    continue;
                }
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

        log.info("Аналитика для карточки nmID {}: создано {}, обновлено {}, пропущено {}",
                card.getNmId(), savedCount, updatedCount, skippedCount);
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

    private List<LocalDate> getExistingAnalyticsDates(Long nmId, Long cabinetId, LocalDate dateFrom, LocalDate dateTo) {
        List<ProductCardAnalytics> existing = cabinetId != null
                ? analyticsRepository.findByCabinet_IdAndProductCardNmIdAndDateBetween(cabinetId, nmId, dateFrom, dateTo)
                : analyticsRepository.findByProductCardNmIdAndDateBetween(nmId, dateFrom, dateTo);
        return existing.stream().map(ProductCardAnalytics::getDate).toList();
    }

    private boolean isAllDatesPresent(List<LocalDate> existingDates, LocalDate dateFrom, LocalDate dateTo) {
        long totalDays = ChronoUnit.DAYS.between(dateFrom, dateTo) + 1;
        if (existingDates.size() < totalDays) return false;
        for (LocalDate date = dateFrom; !date.isAfter(dateTo); date = date.plusDays(1)) {
            if (!existingDates.contains(date)) return false;
        }
        return true;
    }

    private DateRange calculateRequestRange(List<LocalDate> existingDates, LocalDate dateFrom, LocalDate dateTo) {
        LocalDate minMissing = null;
        LocalDate maxMissing = null;
        for (LocalDate date = dateFrom; !date.isAfter(dateTo); date = date.plusDays(1)) {
            if (!existingDates.contains(date)) {
                if (minMissing == null) minMissing = date;
                maxMissing = date;
            }
        }
        if (minMissing == null || maxMissing == null) return null;
        return new DateRange(minMissing, maxMissing);
    }

    private boolean isDateInRange(LocalDate date, LocalDate dateFrom, LocalDate dateTo) {
        return !date.isBefore(dateFrom) && !date.isAfter(dateTo);
    }

    public record ProcessingResult(int successCount, int errorCount) {}

    private record AnalyticsSaveItem(ProductCard card, SaleFunnelResponse.DailyData dailyData, LocalDate date) {}

    private record SaveBatchResult(int savedCount, int updatedCount) {}

    private record DateRange(LocalDate from, LocalDate to) {}
}
