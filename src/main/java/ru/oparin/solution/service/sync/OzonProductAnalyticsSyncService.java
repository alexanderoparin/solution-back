package ru.oparin.solution.service.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.oparin.solution.dto.ozon.OzonAnalyticsDataResponse;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.OzonProductCard;
import ru.oparin.solution.model.OzonProductCardAnalytics;
import ru.oparin.solution.repository.OzonProductCardAnalyticsRepository;
import ru.oparin.solution.repository.OzonProductCardRepository;
import ru.oparin.solution.service.ozon.OzonProductsApiClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Загрузка и сохранение аналитики продаж Ozon ({@code /v1/analytics/data}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OzonProductAnalyticsSyncService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int PAGE_LIMIT = 1000;
    /** Индекс метрики revenue в запросе. */
    private static final int METRIC_REVENUE = 0;
    /** Индекс метрики ordered_units в запросе. */
    private static final int METRIC_ORDERED_UNITS = 1;

    private final OzonProductsApiClient productsApiClient;
    private final OzonProductCardRepository productCardRepository;
    private final OzonProductCardAnalyticsRepository analyticsRepository;

    /**
     * Загружает все страницы аналитики за период и сохраняет в БД.
     */
    public void syncAnalytics(Cabinet cabinet, String clientId, String apiKey, LocalDate dateFrom, LocalDate dateTo) {
        Map<Long, Long> productIdBySku = buildSkuToProductIdMap(cabinet.getId());
        if (productIdBySku.isEmpty()) {
            log.info("Ozon analytics: нет карточек со SKU для cabinetId={}, пропуск", cabinet.getId());
            return;
        }

        String dateFromStr = dateFrom.format(DATE_FORMATTER);
        String dateToStr = dateTo.format(DATE_FORMATTER);
        int offset = 0;
        int pages = 0;
        int rowsTotal = 0;
        int saved = 0;

        while (true) {
            OzonAnalyticsDataResponse response = productsApiClient.getAnalyticsData(
                    clientId, apiKey, dateFromStr, dateToStr, PAGE_LIMIT, offset
            );
            pages++;
            List<OzonAnalyticsDataResponse.Row> rows = response != null && response.getResult() != null
                    ? response.getResult().getData()
                    : null;
            if (rows == null || rows.isEmpty()) {
                break;
            }
            rowsTotal += rows.size();
            saved += saveRows(cabinet, rows, productIdBySku, dateFrom, dateTo);
            if (rows.size() < PAGE_LIMIT) {
                break;
            }
            offset += PAGE_LIMIT;
        }

        log.info("Ozon analytics cabinetId={}: страниц={}, строк={}, сохранено/обновлено={}, период={}..{}",
                cabinet.getId(), pages, rowsTotal, saved, dateFrom, dateTo);
    }

    private Map<Long, Long> buildSkuToProductIdMap(Long cabinetId) {
        Map<Long, Long> map = new HashMap<>();
        for (OzonProductCard card : productCardRepository.findByCabinet_IdOrderByProductIdAsc(cabinetId)) {
            if (card.getSku() != null && card.getProductId() != null) {
                map.putIfAbsent(card.getSku(), card.getProductId());
            }
        }
        return map;
    }

    private int saveRows(
            Cabinet cabinet,
            List<OzonAnalyticsDataResponse.Row> rows,
            Map<Long, Long> productIdBySku,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        List<OzonProductCardAnalytics> toSave = new ArrayList<>();
        for (OzonAnalyticsDataResponse.Row row : rows) {
            ParsedRow parsed = parseRow(row);
            if (parsed == null) {
                continue;
            }
            if (parsed.date().isBefore(dateFrom) || parsed.date().isAfter(dateTo)) {
                continue;
            }
            Long productId = productIdBySku.get(parsed.sku());
            if (productId == null) {
                log.debug("Ozon analytics: SKU {} не найден в каталоге cabinetId={}", parsed.sku(), cabinet.getId());
                continue;
            }
            OzonProductCardAnalytics entity = analyticsRepository
                    .findByCabinet_IdAndProductIdAndDate(cabinet.getId(), productId, parsed.date())
                    .orElseGet(() -> OzonProductCardAnalytics.builder()
                            .cabinet(cabinet)
                            .productId(productId)
                            .date(parsed.date())
                            .build());
            entity.setSku(parsed.sku());
            entity.setOrderedUnits(parsed.orderedUnits());
            entity.setRevenue(parsed.revenue());
            toSave.add(entity);
        }
        if (!toSave.isEmpty()) {
            analyticsRepository.saveAll(toSave);
        }
        return toSave.size();
    }

    private static ParsedRow parseRow(OzonAnalyticsDataResponse.Row row) {
        if (row == null || row.getDimensions() == null || row.getDimensions().size() < 2) {
            return null;
        }
        Long sku = parseLong(row.getDimensions().get(0).getId());
        LocalDate date = parseDate(row.getDimensions().get(1).getId());
        if (sku == null || date == null) {
            return null;
        }
        List<Double> metrics = row.getMetrics();
        BigDecimal revenue = metricAsMoney(metrics, METRIC_REVENUE);
        Integer orderedUnits = metricAsInt(metrics, METRIC_ORDERED_UNITS);
        return new ParsedRow(sku, date, orderedUnits, revenue);
    }

    private static Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), DATE_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal metricAsMoney(List<Double> metrics, int index) {
        if (metrics == null || index >= metrics.size() || metrics.get(index) == null) {
            return null;
        }
        return BigDecimal.valueOf(metrics.get(index)).setScale(2, RoundingMode.HALF_UP);
    }

    private static Integer metricAsInt(List<Double> metrics, int index) {
        if (metrics == null || index >= metrics.size() || metrics.get(index) == null) {
            return null;
        }
        return (int) Math.round(metrics.get(index));
    }

    private record ParsedRow(Long sku, LocalDate date, Integer orderedUnits, BigDecimal revenue) {
    }
}
