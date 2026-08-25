package ru.oparin.solution.service.ozon;

import lombok.extern.slf4j.Slf4j;
import ru.oparin.solution.dto.ozon.OzonPerformanceSearchPhrasesResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Парсер CSV-отчёта Performance API по поисковым запросам (разделитель {@code ;}).
 * Поддерживает секции «Кампания по продвижению…» и вложенный CSV внутри JSON.
 */
@Slf4j
public final class OzonPerformanceSearchPhrasesCsvParser {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Pattern CAMPAIGN_ID_IN_COMMENT = Pattern.compile("№\\s*(\\d+)");
    private static final Pattern DATE_IN_TEXT = Pattern.compile("\\b(\\d{2}\\.\\d{2}\\.\\d{4})\\b");

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
        List<OzonPerformanceSearchPhrasesResponse.Row> rows = parseInternal(
                stripBom(csvBody), fallbackCampaignId, fallbackDateFrom, fallbackDateTo);
        log.info("Ozon search phrases CSV: распознано {} строк", rows.size());
        return rows;
    }

    private static List<OzonPerformanceSearchPhrasesResponse.Row> parseInternal(
            String csvBody,
            Long fallbackCampaignId,
            LocalDate fallbackDateFrom,
            LocalDate fallbackDateTo
    ) {
        String[] lines = csvBody.split("\\r?\\n");
        Map<String, Integer> columnIndex = null;
        List<OzonPerformanceSearchPhrasesResponse.Row> rows = new ArrayList<>();
        Long currentCampaignId = fallbackCampaignId;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (isReportHeaderLine(line)) {
                Long parsedCampaignId = parseCampaignIdFromComment(line);
                if (parsedCampaignId != null) {
                    currentCampaignId = parsedCampaignId;
                }
                columnIndex = null;
                continue;
            }

            String[] cols = line.split(";", -1);
            if (line.startsWith(";")) {
                Long parsedCampaignId = parseCampaignIdFromComment(line);
                if (parsedCampaignId != null) {
                    currentCampaignId = parsedCampaignId;
                    columnIndex = null;
                    continue;
                }
                if (columnIndex == null && looksLikeHeader(cols)) {
                    columnIndex = mapHeaderColumns(cols);
                    continue;
                }
                if (columnIndex != null) {
                    OzonPerformanceSearchPhrasesResponse.Row row = toRow(
                            cols, columnIndex, currentCampaignId, fallbackDateFrom, fallbackDateTo);
                    if (row != null && row.isValidDataRow()) {
                        rows.add(row);
                    }
                }
                continue;
            }

            if (columnIndex == null) {
                if (looksLikeHeader(cols)) {
                    columnIndex = mapHeaderColumns(cols);
                }
                continue;
            }

            OzonPerformanceSearchPhrasesResponse.Row row = toRow(
                    cols, columnIndex, currentCampaignId, fallbackDateFrom, fallbackDateTo);
            if (row != null && row.isValidDataRow()) {
                rows.add(row);
            }
        }
        return rows;
    }

    private static String stripBom(String csvBody) {
        if (!csvBody.isEmpty() && csvBody.charAt(0) == '\uFEFF') {
            return csvBody.substring(1);
        }
        return csvBody;
    }

    private static boolean isReportHeaderLine(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("кампания по продвижению") || lower.contains("campaign promotion");
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
            if (lower.contains("запрос") || lower.contains("phrase") || lower.contains("query")
                    || lower.contains("фраз") || lower.contains("кластер") || lower.contains("criteria")) {
                return true;
            }
            if (lower.equals("дата") || lower.contains("date")) {
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
            } else if (lower.contains("позиц") || lower.contains("position")) {
                map.put("avgPos", i);
            } else if (lower.equals("дата") || (lower.contains("дата") && !lower.contains("добавления"))) {
                map.put("date", i);
            }
        }
        if (!map.containsKey("phrase")) {
            for (int i = 0; i < cols.length; i++) {
                String lower = cols[i].trim().toLowerCase(Locale.ROOT);
                if (!lower.isEmpty() && !lower.equals("дата") && !lower.contains("date")) {
                    map.putIfAbsent("phrase", i);
                    break;
                }
            }
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
        if (phrase.isEmpty() || OzonPerformanceSearchPhrasesResponse.isReportHeaderPhrase(phrase)) {
            return null;
        }
        LocalDate date = readColDate(cols, columnIndex.get("date"));
        if (date == null) {
            date = findDateInRow(cols, columnIndex);
        }
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

    private static LocalDate findDateInRow(String[] cols, Map<String, Integer> columnIndex) {
        Integer phraseIdx = columnIndex.get("phrase");
        for (int i = cols.length - 1; i >= 0; i--) {
            if (phraseIdx != null && phraseIdx == i) {
                continue;
            }
            LocalDate date = parseDate(cols[i]);
            if (date != null) {
                return date;
            }
        }
        for (String col : cols) {
            Matcher matcher = DATE_IN_TEXT.matcher(col);
            if (matcher.find()) {
                LocalDate date = parseDate(matcher.group(1));
                if (date != null) {
                    return date;
                }
            }
        }
        return null;
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
