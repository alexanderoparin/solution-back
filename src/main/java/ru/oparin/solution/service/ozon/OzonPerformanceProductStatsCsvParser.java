package ru.oparin.solution.service.ozon;

import lombok.extern.slf4j.Slf4j;
import ru.oparin.solution.dto.ozon.OzonPerformanceProductStatsResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Парсер CSV-отчёта Performance API по SKU (разделитель {@code ;}, десятичная запятая).
 */
@Slf4j
public final class OzonPerformanceProductStatsCsvParser {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private OzonPerformanceProductStatsCsvParser() {
    }

    /**
     * Разбирает CSV-тело отчёта; пропускает заголовки и строки «Bcero/Всего».
     */
    public static List<OzonPerformanceProductStatsResponse.Row> parse(String csvBody, Long fallbackCampaignId) {
        if (csvBody == null || csvBody.isBlank()) {
            return List.of();
        }
        String[] lines = csvBody.split("\\r?\\n");
        List<OzonPerformanceProductStatsResponse.Row> rows = new ArrayList<>();
        boolean headerPassed = false;
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith(";")) {
                continue;
            }
            if (!headerPassed) {
                if (line.toLowerCase(Locale.ROOT).contains("sku")) {
                    headerPassed = true;
                }
                continue;
            }
            String[] cols = line.split(";", -1);
            if (cols.length < 4) {
                continue;
            }
            String skuText = cols[0].trim();
            if (skuText.isEmpty() || isTotalRow(skuText)) {
                continue;
            }
            Long sku = parseLong(skuText);
            if (sku == null) {
                continue;
            }
            OzonPerformanceProductStatsResponse.Row row = new OzonPerformanceProductStatsResponse.Row();
            row.setCampaignId(fallbackCampaignId);
            row.setSku(sku);
            LocalDate rowDate = null;
            if (cols.length > 15) {
                rowDate = parseDate(cols[15]);
            }
            if (rowDate == null) {
                continue;
            }
            row.setDate(rowDate);
            if (cols.length > 3) {
                row.setViews(parseInt(cols[3]));
            }
            if (cols.length > 4) {
                row.setClicks(parseInt(cols[4]));
            }
            if (cols.length > 5) {
                row.setCtr(parseDecimal(cols[5]));
            }
            if (cols.length > 6) {
                row.setToCart(parseInt(cols[6]));
            }
            if (cols.length > 7) {
                row.setAvgCpc(parseDecimal(cols[7]));
            }
            if (cols.length > 9) {
                row.setSpend(parseDecimal(cols[9]));
            }
            if (cols.length > 10) {
                row.setOrders(parseInt(cols[10]));
            }
            if (cols.length > 11) {
                row.setOrdersMoney(parseDecimal(cols[11]));
            }
            if (cols.length > 12) {
                row.setModelOrders(parseInt(cols[12]));
            }
            if (cols.length > 13) {
                row.setModelSales(parseDecimal(cols[13]));
            }
            if (cols.length > 14) {
                row.setDrr(parseDecimal(cols[14]));
            }
            rows.add(row);
        }
        log.info("Ozon product stats CSV: распознано {} строк SKU", rows.size());
        return rows;
    }

    private static boolean isTotalRow(String skuText) {
        String lower = skuText.toLowerCase(Locale.ROOT);
        return lower.startsWith("bcero") || lower.startsWith("всего") || lower.startsWith("total");
    }

    private static Long parseLong(String text) {
        try {
            return Long.parseLong(text.replace(" ", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInt(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.trim().replace(" ", "").replace(',', '.');
        try {
            return (int) Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal parseDecimal(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.trim().replace(" ", "").replace(',', '.');
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.length() >= 10 && trimmed.charAt(4) == '-') {
            try {
                return LocalDate.parse(trimmed.substring(0, 10));
            } catch (DateTimeParseException ignored) {
                // fallback
            }
        }
        try {
            return LocalDate.parse(trimmed, DMY);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
