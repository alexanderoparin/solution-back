package ru.oparin.solution.service.ozon;

import lombok.extern.slf4j.Slf4j;
import ru.oparin.solution.dto.ozon.OzonPerformanceProductStatsResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Парсер CSV-отчёта Performance API по SKU (разделитель {@code ;}, десятичная запятая).
 * Поддерживает секции с несколькими кампаниями и заголовки Ozon на русском.
 */
@Slf4j
public final class OzonPerformanceProductStatsCsvParser {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Pattern CAMPAIGN_ID_IN_COMMENT = Pattern.compile("№\\s*(\\d+)");
    private static final Pattern DATE_IN_TEXT = Pattern.compile("\\b(\\d{2}\\.\\d{2}\\.\\d{4})\\b");

    private OzonPerformanceProductStatsCsvParser() {
    }

    /**
     * Разбирает CSV-тело отчёта; пропускает заголовки и строки «Bcero/Всего».
     */
    public static List<OzonPerformanceProductStatsResponse.Row> parse(String csvBody, Long fallbackCampaignId) {
        if (csvBody == null || csvBody.isBlank()) {
            return List.of();
        }
        String normalizedBody = stripBom(csvBody);
        String[] lines = normalizedBody.split("\\r?\\n");
        List<OzonPerformanceProductStatsResponse.Row> rows = new ArrayList<>();
        Map<String, Integer> columnIndex = null;
        Long currentCampaignId = fallbackCampaignId;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith(";")) {
                Long parsedCampaignId = parseCampaignIdFromComment(line);
                if (parsedCampaignId != null) {
                    currentCampaignId = parsedCampaignId;
                    columnIndex = null;
                }
                continue;
            }

            String[] cols = line.split(";", -1);
            if (columnIndex == null) {
                if (looksLikeHeader(cols)) {
                    columnIndex = mapHeaderColumns(cols);
                }
                continue;
            }

            OzonPerformanceProductStatsResponse.Row row = toRow(cols, columnIndex, currentCampaignId);
            if (row != null) {
                rows.add(row);
            }
        }
        log.info("Ozon product stats CSV: распознано {} строк SKU", rows.size());
        return rows;
    }

    private static String stripBom(String csvBody) {
        if (!csvBody.isEmpty() && csvBody.charAt(0) == '\uFEFF') {
            return csvBody.substring(1);
        }
        return csvBody;
    }

    private static Long parseCampaignIdFromComment(String line) {
        Matcher matcher = CAMPAIGN_ID_IN_COMMENT.matcher(line);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean looksLikeHeader(String[] cols) {
        for (String col : cols) {
            String lower = col.trim().toLowerCase(Locale.ROOT);
            if (lower.equals("sku") || lower.contains("ozon id") || lower.contains("артикул")) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Integer> mapHeaderColumns(String[] cols) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < cols.length; i++) {
            String lower = cols[i].trim().toLowerCase(Locale.ROOT);
            if (lower.equals("sku") || lower.contains("ozon id") || lower.contains("артикул")) {
                map.put("sku", i);
            } else if (lower.equals("дата") || (lower.contains("дата") && !lower.contains("дата добавления"))) {
                map.put("date", i);
            } else if (lower.contains("дата добавления") || lower.contains("дрр")) {
                map.putIfAbsent("drr", i);
            } else if (lower.contains("показ")) {
                map.put("views", i);
            } else if (lower.contains("клик") && !lower.contains("цена")) {
                map.put("clicks", i);
            } else if (lower.contains("ctr")) {
                map.put("ctr", i);
            } else if (lower.contains("корзин")) {
                map.put("toCart", i);
            } else if (lower.contains("цена клика") || lower.contains("cpc")) {
                map.put("avgCpc", i);
            } else if (lower.contains("расход") || lower.contains("затрат")) {
                map.put("spend", i);
            } else if (lower.contains("заказ")) {
                map.put("orders", i);
            } else if (lower.contains("выруч")) {
                map.put("ordersMoney", i);
            } else if (lower.contains("модел") && lower.contains("заказ")) {
                map.put("modelOrders", i);
            } else if (lower.contains("модел") && lower.contains("выруч")) {
                map.put("modelSales", i);
            }
        }
        if (!map.containsKey("sku") && cols.length > 0) {
            map.put("sku", 0);
        }
        return map;
    }

    private static OzonPerformanceProductStatsResponse.Row toRow(
            String[] cols,
            Map<String, Integer> columnIndex,
            Long fallbackCampaignId
    ) {
        Integer skuIdx = columnIndex.get("sku");
        if (skuIdx == null || skuIdx >= cols.length) {
            return null;
        }
        String skuText = cols[skuIdx].trim();
        if (skuText.isEmpty() || isTotalRow(skuText) || parseDate(skuText) != null) {
            return null;
        }
        Long sku = parseLong(skuText);
        if (sku == null) {
            return null;
        }

        LocalDate rowDate = readColDate(cols, columnIndex.get("date"));
        if (rowDate == null) {
            rowDate = findDateInRow(cols, columnIndex);
        }
        if (rowDate == null) {
            return null;
        }

        OzonPerformanceProductStatsResponse.Row row = new OzonPerformanceProductStatsResponse.Row();
        row.setCampaignId(fallbackCampaignId);
        row.setSku(sku);
        row.setDate(rowDate);
        row.setViews(readColInt(cols, columnIndex.get("views")));
        row.setClicks(readColInt(cols, columnIndex.get("clicks")));
        row.setCtr(readColDecimal(cols, columnIndex.get("ctr")));
        row.setToCart(readColInt(cols, columnIndex.get("toCart")));
        row.setAvgCpc(readColDecimal(cols, columnIndex.get("avgCpc")));
        row.setSpend(readColDecimal(cols, columnIndex.get("spend")));
        row.setOrders(readColInt(cols, columnIndex.get("orders")));
        row.setOrdersMoney(readColDecimal(cols, columnIndex.get("ordersMoney")));
        row.setModelOrders(readColInt(cols, columnIndex.get("modelOrders")));
        row.setModelSales(readColDecimal(cols, columnIndex.get("modelSales")));
        row.setDrr(readColDecimal(cols, columnIndex.get("drr")));
        return row;
    }

    private static LocalDate findDateInRow(String[] cols, Map<String, Integer> columnIndex) {
        Integer drrIdx = columnIndex.get("drr");
        if (drrIdx != null && drrIdx < cols.length) {
            LocalDate fromDrrCell = extractDateFromText(cols[drrIdx]);
            if (fromDrrCell != null) {
                return fromDrrCell;
            }
        }
        for (int i = cols.length - 1; i >= 0; i--) {
            Integer skuColumn = columnIndex.get("sku");
            if (skuColumn != null && skuColumn == i) {
                continue;
            }
            LocalDate date = parseDate(cols[i]);
            if (date != null) {
                return date;
            }
        }
        return null;
    }

    private static LocalDate extractDateFromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = DATE_IN_TEXT.matcher(text.trim());
        if (matcher.find()) {
            return parseDate(matcher.group(1));
        }
        return null;
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

    private static Integer readColInt(String[] cols, Integer idx) {
        if (idx == null || idx >= cols.length) {
            return null;
        }
        return parseInt(cols[idx]);
    }

    private static BigDecimal readColDecimal(String[] cols, Integer idx) {
        if (idx == null || idx >= cols.length) {
            return null;
        }
        return parseDecimal(cols[idx]);
    }

    private static LocalDate readColDate(String[] cols, Integer idx) {
        if (idx == null || idx >= cols.length) {
            return null;
        }
        return parseDate(cols[idx]);
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
