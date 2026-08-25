package ru.oparin.solution.service.ozon;

import lombok.extern.slf4j.Slf4j;
import ru.oparin.solution.dto.ozon.OzonPerformanceSearchPhrasesResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Парсер CSV-отчёта Performance API по поисковым запросам (разделитель {@code ;}).
 */
@Slf4j
public final class OzonPerformanceSearchPhrasesCsvParser {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private OzonPerformanceSearchPhrasesCsvParser() {
    }

    /**
     * Разбирает CSV-тело отчёта; определяет колонки по заголовку.
     */
    public static List<OzonPerformanceSearchPhrasesResponse.Row> parse(
            String csvBody,
            Long fallbackCampaignId,
            LocalDate fallbackDateFrom,
            LocalDate fallbackDateTo
    ) {
        if (csvBody == null || csvBody.isBlank()) {
            return List.of();
        }
        String normalized = csvBody.replace("\uFEFF", "");
        String[] lines = normalized.split("\\r?\\n");
        Map<String, Integer> columnIndex = null;
        List<OzonPerformanceSearchPhrasesResponse.Row> rows = new ArrayList<>();
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith(";")) {
                continue;
            }
            String[] cols = line.split(";", -1);
            if (columnIndex == null) {
                if (looksLikeHeader(cols)) {
                    columnIndex = mapHeaderColumns(cols);
                }
                continue;
            }
            OzonPerformanceSearchPhrasesResponse.Row row = toRow(
                    cols, columnIndex, fallbackCampaignId, fallbackDateFrom, fallbackDateTo);
            if (row != null && !row.isTotalRow()) {
                rows.add(row);
            }
        }
        log.info("Ozon search phrases CSV: распознано {} строк", rows.size());
        return rows;
    }

    private static boolean looksLikeHeader(String[] cols) {
        for (String col : cols) {
            String lower = col.trim().toLowerCase(Locale.ROOT);
            if (lower.contains("запрос") || lower.contains("phrase") || lower.contains("query")
                    || lower.contains("фраз") || lower.contains("кластер") || lower.contains("criteria")) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Integer> mapHeaderColumns(String[] cols) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < cols.length; i++) {
            String lower = cols[i].trim().toLowerCase(Locale.ROOT);
            if (lower.contains("запрос") || lower.contains("phrase") || lower.contains("query")
                    || lower.contains("фраз") || lower.contains("кластер") || lower.contains("criteria")) {
                map.put("phrase", i);
            } else if (lower.equals("sku") || lower.contains("ozon id")) {
                map.put("sku", i);
            } else if (lower.contains("показ")) {
                map.put("views", i);
            } else if (lower.contains("клик")) {
                map.put("clicks", i);
            } else if (lower.contains("ctr")) {
                map.put("ctr", i);
            } else if (lower.contains("корзин")) {
                map.put("toCart", i);
            } else if (lower.contains("цена клика") || lower.contains("cpc") || lower.contains("клика,")) {
                map.put("avgCpc", i);
            } else if (lower.contains("расход") || lower.contains("затрат")) {
                map.put("spend", i);
            } else if (lower.contains("заказ")) {
                map.put("orders", i);
            } else if (lower.contains("позиц") || lower.contains("position")) {
                map.put("avgPos", i);
            } else if (lower.contains("дата")) {
                map.put("date", i);
            }
        }
        if (!map.containsKey("phrase") && cols.length > 0) {
            map.put("phrase", 0);
        }
        return map;
    }

    private static OzonPerformanceSearchPhrasesResponse.Row toRow(
            String[] cols,
            Map<String, Integer> columnIndex,
            Long fallbackCampaignId,
            LocalDate fallbackDateFrom,
            LocalDate fallbackDateTo
    ) {
        Integer phraseIdx = columnIndex.get("phrase");
        if (phraseIdx == null || phraseIdx >= cols.length) {
            return null;
        }
        String phrase = cols[phraseIdx].trim();
        if (phrase.isEmpty()) {
            return null;
        }
        LocalDate date = readColDate(cols, columnIndex.get("date"));
        if (date == null) {
            date = fallbackDateTo != null ? fallbackDateTo : fallbackDateFrom;
        }
        if (date == null) {
            return null;
        }
        OzonPerformanceSearchPhrasesResponse.Row row = new OzonPerformanceSearchPhrasesResponse.Row();
        row.setCampaignId(fallbackCampaignId);
        row.setSearchPhrase(phrase);
        row.setDate(date);
        row.setSku(readColLong(cols, columnIndex.get("sku")));
        row.setViews(readColInt(cols, columnIndex.get("views")));
        row.setClicks(readColInt(cols, columnIndex.get("clicks")));
        row.setCtr(readColDecimal(cols, columnIndex.get("ctr")));
        row.setToCart(readColInt(cols, columnIndex.get("toCart")));
        row.setAvgCpc(readColDecimal(cols, columnIndex.get("avgCpc")));
        row.setSpend(readColDecimal(cols, columnIndex.get("spend")));
        row.setOrders(readColInt(cols, columnIndex.get("orders")));
        row.setAvgPos(readColDecimal(cols, columnIndex.get("avgPos")));
        return row;
    }

    private static Long readColLong(String[] cols, Integer idx) {
        if (idx == null || idx >= cols.length) {
            return null;
        }
        String text = cols[idx].trim().replace(" ", "");
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer readColInt(String[] cols, Integer idx) {
        if (idx == null || idx >= cols.length) {
            return null;
        }
        String text = cols[idx].trim().replace(" ", "").replace(',', '.');
        if (text.isEmpty()) {
            return null;
        }
        try {
            return (int) Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal readColDecimal(String[] cols, Integer idx) {
        if (idx == null || idx >= cols.length) {
            return null;
        }
        String text = cols[idx].trim().replace(" ", "").replace(',', '.');
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate readColDate(String[] cols, Integer idx) {
        if (idx == null || idx >= cols.length) {
            return null;
        }
        return parseDate(cols[idx]);
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
