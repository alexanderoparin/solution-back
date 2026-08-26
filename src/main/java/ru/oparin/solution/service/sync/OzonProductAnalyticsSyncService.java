package ru.oparin.solution.service.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import ru.oparin.solution.dto.ozon.OzonAnalyticsDataResponse;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.OzonProductCard;
import ru.oparin.solution.model.OzonProductCardAnalytics;
import ru.oparin.solution.repository.OzonProductCardAnalyticsRepository;
import ru.oparin.solution.repository.OzonProductCardRepository;
import ru.oparin.solution.service.OzonSellerSubscriptionService;
import ru.oparin.solution.service.ozon.OzonProductsApiClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Загрузка и сохранение аналитики продаж Ozon ({@code /v1/analytics/data}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OzonProductAnalyticsSyncService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int PAGE_LIMIT = 1000;

    private static final List<String> EXTENDED_METRICS = List.of(
            "hits_view_pdp", "hits_tocart", "conv_tocart", "ordered_units", "revenue"
    );
    private static final List<String> BASIC_METRICS = List.of("ordered_units", "revenue");

    private final OzonProductsApiClient productsApiClient;
    private final OzonProductCardRepository productCardRepository;
    private final OzonProductCardAnalyticsRepository analyticsRepository;
    private final OzonSellerSubscriptionService ozonSellerSubscriptionService;

    /**
     * Загружает все страницы аналитики за период и сохраняет в БД.
     */
    public void syncAnalytics(Cabinet cabinet, String clientId, String apiKey, LocalDate dateFrom, LocalDate dateTo) {
        Map<Long, Long> productIdBySku = buildSkuToProductIdMap(cabinet.getId());
        if (productIdBySku.isEmpty()) {
            log.info("Ozon analytics: нет карточек со SKU для cabinetId={}, пропуск", cabinet.getId());
            return;
        }

        MetricsLayout layout = resolveMetricsLayout(clientId, apiKey, dateFrom, dateTo);
        String dateFromStr = dateFrom.format(DATE_FORMATTER);
        String dateToStr = dateTo.format(DATE_FORMATTER);
        int offset = 0;
        int pages = 0;
        int rowsTotal = 0;
        int saved = 0;
        int skippedUnknownSku = 0;
        Set<Long> unknownSkus = new HashSet<>();

        while (true) {
            OzonAnalyticsDataResponse response = productsApiClient.getAnalyticsData(
                    clientId, apiKey, dateFromStr, dateToStr, PAGE_LIMIT, offset, layout.metricNames()
            );
            pages++;
            List<OzonAnalyticsDataResponse.Row> rows = response != null && response.getResult() != null
                    ? response.getResult().getData()
                    : null;
            if (rows == null || rows.isEmpty()) {
                break;
            }
            rowsTotal += rows.size();
            SaveResult pageResult = saveRows(cabinet, rows, productIdBySku, dateFrom, dateTo, unknownSkus, layout);
            saved += pageResult.saved();
            skippedUnknownSku += pageResult.skippedUnknownSku();
            if (rows.size() < PAGE_LIMIT) {
                break;
            }
            offset += PAGE_LIMIT;
        }

        log.info("Ozon analytics cabinetId={}: метрики={}, страниц={}, строк={}, сохранено/обновлено={}, "
                        + "пропущено без карточки={} (уник. SKU={}), период={}..{}",
                cabinet.getId(), layout.metricNames(), pages, rowsTotal, saved, skippedUnknownSku,
                unknownSkus.size(), dateFrom, dateTo);
        ozonSellerSubscriptionService.updateFunnelAvailability(cabinet.getId(), layout.useExtendedMetrics());
    }

    /**
     * Пробный запрос: расширенные метрики воронки или только ordered_units/revenue.
     */
    private MetricsLayout resolveMetricsLayout(
            String clientId,
            String apiKey,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        try {
            OzonAnalyticsDataResponse probe = productsApiClient.getAnalyticsData(
                    clientId,
                    apiKey,
                    dateFrom.format(DATE_FORMATTER),
                    dateTo.format(DATE_FORMATTER),
                    1,
                    0,
                    EXTENDED_METRICS
            );
            if (probe != null && responseHasExtendedMetrics(probe)) {
                return MetricsLayout.extended();
            }
            if (probe != null) {
                log.info("Ozon analytics: API вернул меньше {} метрик, используем базовый набор",
                        EXTENDED_METRICS.size());
            }
        } catch (HttpClientErrorException e) {
            log.info("Ozon analytics: расширенные метрики недоступны (HTTP {}), используем базовый набор",
                    e.getStatusCode().value());
        } catch (Exception e) {
            log.info("Ozon analytics: расширенные метрики недоступны ({}), используем базовый набор",
                    e.getMessage());
        }
        return MetricsLayout.basic();
    }

    /**
     * Ozon может ответить HTTP 200 на запрос расширенных метрик, но вернуть только ordered_units/revenue.
     */
    private static boolean responseHasExtendedMetrics(OzonAnalyticsDataResponse response) {
        if (response.getResult() == null || response.getResult().getData() == null) {
            return false;
        }
        for (OzonAnalyticsDataResponse.Row row : response.getResult().getData()) {
            List<Double> metrics = row.getMetrics();
            if (metrics != null && metrics.size() >= EXTENDED_METRICS.size()) {
                return true;
            }
        }
        return false;
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

    private SaveResult saveRows(
            Cabinet cabinet,
            List<OzonAnalyticsDataResponse.Row> rows,
            Map<Long, Long> productIdBySku,
            LocalDate dateFrom,
            LocalDate dateTo,
            Set<Long> unknownSkus,
            MetricsLayout layout
    ) {
        List<OzonProductCardAnalytics> toSave = new ArrayList<>();
        int skippedUnknownSku = 0;
        for (OzonAnalyticsDataResponse.Row row : rows) {
            ParsedRow parsed = parseRow(row, layout);
            if (parsed == null) {
                continue;
            }
            if (parsed.date().isBefore(dateFrom) || parsed.date().isAfter(dateTo)) {
                continue;
            }
            Long productId = productIdBySku.get(parsed.sku());
            if (productId == null) {
                unknownSkus.add(parsed.sku());
                skippedUnknownSku++;
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
            if (layout.useExtendedMetrics()) {
                entity.setHitsViewPdp(parsed.hitsViewPdp());
                entity.setHitsTocart(parsed.hitsTocart());
                entity.setConvTocart(parsed.convTocart());
            } else {
                entity.setHitsViewPdp(null);
                entity.setHitsTocart(null);
                entity.setConvTocart(null);
            }
            toSave.add(entity);
        }
        if (!toSave.isEmpty()) {
            analyticsRepository.saveAll(toSave);
        }
        return new SaveResult(toSave.size(), skippedUnknownSku);
    }

    private static ParsedRow parseRow(OzonAnalyticsDataResponse.Row row, MetricsLayout layout) {
        if (row == null || row.getDimensions() == null || row.getDimensions().size() < 2) {
            return null;
        }
        Long sku = parseLong(row.getDimensions().get(0).getId());
        LocalDate date = parseDate(row.getDimensions().get(1).getId());
        if (sku == null || date == null) {
            return null;
        }
        List<Double> metrics = row.getMetrics();
        if (layout.useExtendedMetrics()) {
            return new ParsedRow(
                    sku,
                    date,
                    metricAsInt(metrics, layout.orderedUnitsIndex()),
                    metricAsMoney(metrics, layout.revenueIndex()),
                    metricAsInt(metrics, layout.hitsViewPdpIndex()),
                    metricAsInt(metrics, layout.hitsTocartIndex()),
                    metricAsPercent(metrics, layout.convTocartIndex())
            );
        }
        return new ParsedRow(
                sku,
                date,
                metricAsInt(metrics, layout.orderedUnitsIndex()),
                metricAsMoney(metrics, layout.revenueIndex()),
                null,
                null,
                null
        );
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

    private static BigDecimal metricAsPercent(List<Double> metrics, int index) {
        if (metrics == null || index >= metrics.size() || metrics.get(index) == null) {
            return null;
        }
        return BigDecimal.valueOf(metrics.get(index)).setScale(4, RoundingMode.HALF_UP);
    }

    private record ParsedRow(
            Long sku,
            LocalDate date,
            Integer orderedUnits,
            BigDecimal revenue,
            Integer hitsViewPdp,
            Integer hitsTocart,
            BigDecimal convTocart
    ) {
    }

    private record SaveResult(int saved, int skippedUnknownSku) {
    }

    private record MetricsLayout(List<String> metricNames, boolean useExtendedMetrics) {
        static MetricsLayout extended() {
            return new MetricsLayout(EXTENDED_METRICS, true);
        }

        static MetricsLayout basic() {
            return new MetricsLayout(BASIC_METRICS, false);
        }

        int hitsViewPdpIndex() {
            return 0;
        }

        int hitsTocartIndex() {
            return 1;
        }

        int convTocartIndex() {
            return 2;
        }

        int orderedUnitsIndex() {
            return useExtendedMetrics ? 3 : 0;
        }

        int revenueIndex() {
            return useExtendedMetrics ? 4 : 1;
        }
    }
}
