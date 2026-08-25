package ru.oparin.solution.dto.ozon;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Разбор JSON/CSV-отчёта Performance API по поисковым запросам (POST /statistics/phrases).
 */
@Slf4j
public final class OzonPerformanceSearchPhrasesResponse {

    private static final Set<String> SKIP_NESTED_FIELDS = Set.of(
            "rows", "report", "campaigns", "meta", "request", "title", "name", "header", "headers"
    );

    private static final Set<String> NESTED_CONTAINER_FIELDS = Set.of(
            "report", "statistics", "stats", "stat", "data", "metrics", "values", "items",
            "phrases", "queries", "searchPhrases", "search_phrases"
    );

    private OzonPerformanceSearchPhrasesResponse() {
    }

    /**
     * Разбирает JSON-тело отчёта в плоский список строк по фразам.
     */
    public static List<Row> parseRows(JsonNode root) {
        return parseRows(root, null, null);
    }

    /**
     * Разбирает JSON с запасной датой периода (если в строке нет поля date).
     */
    public static List<Row> parseRows(JsonNode root, LocalDate fallbackDateFrom, LocalDate fallbackDateTo) {
        if (root == null || root.isNull()) {
            return List.of();
        }
        List<Row> result = new ArrayList<>();
        collectRows(root, null, null, fallbackDateFrom, fallbackDateTo, result);
        if (result.isEmpty()) {
            collectAggressiveFallback(root, null, null, fallbackDateFrom, fallbackDateTo, result);
        }
        if (result.isEmpty() && root.isObject()) {
            List<String> keys = new ArrayList<>();
            root.fieldNames().forEachRemaining(keys::add);
            log.warn("Ozon search phrases JSON: 0 строк, корневые ключи: {}", keys);
            logSampleStructure(root, 0);
        } else {
            log.info("Ozon search phrases JSON: распознано {} строк", result.size());
        }
        return result;
    }

    private static void collectRows(
            JsonNode node,
            Long inheritedCampaignId,
            LocalDate inheritedDate,
            LocalDate fallbackDateFrom,
            LocalDate fallbackDateTo,
            List<Row> out
    ) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode element : node) {
                if (element != null && element.isArray()) {
                    Row arrayRow = toRowFromArray(element, inheritedCampaignId, inheritedDate, fallbackDateTo);
                    if (arrayRow != null && !arrayRow.isTotalRow()) {
                        out.add(arrayRow);
                        continue;
                    }
                }
                if (tryAddRow(element, inheritedCampaignId, inheritedDate, fallbackDateFrom, fallbackDateTo, out)) {
                    continue;
                }
                collectRows(element, inheritedCampaignId, inheritedDate, fallbackDateFrom, fallbackDateTo, out);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }

        if (tryAddRow(node, inheritedCampaignId, inheritedDate, fallbackDateFrom, fallbackDateTo, out)) {
            return;
        }

        Long campaignId = readLong(node, "campaignId", "campaign_id");
        if (campaignId == null && inheritedCampaignId == null) {
            campaignId = readLong(node, "id");
        }
        Long contextCampaignId = campaignId != null ? campaignId : inheritedCampaignId;

        JsonNode report = node.get("report");
        if (report != null) {
            collectRows(report, contextCampaignId, inheritedDate, fallbackDateFrom, fallbackDateTo, out);
        }

        JsonNode rows = node.get("rows");
        if (rows != null && rows.isArray()) {
            for (JsonNode rowNode : rows) {
                if (rowNode != null && rowNode.isArray()) {
                    Row arrayRow = toRowFromArray(rowNode, contextCampaignId, inheritedDate, fallbackDateTo);
                    if (arrayRow != null && !arrayRow.isTotalRow()) {
                        out.add(arrayRow);
                    }
                    continue;
                }
                tryAddRow(rowNode, contextCampaignId, inheritedDate, fallbackDateFrom, fallbackDateTo, out);
            }
        }

        JsonNode campaigns = node.get("campaigns");
        if (campaigns != null) {
            collectRows(campaigns, contextCampaignId, inheritedDate, fallbackDateFrom, fallbackDateTo, out);
        }

        for (String nestedField : NESTED_CONTAINER_FIELDS) {
            JsonNode nested = node.get(nestedField);
            if (nested != null) {
                collectRows(nested, contextCampaignId, inheritedDate, fallbackDateFrom, fallbackDateTo, out);
            }
        }

        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            if (SKIP_NESTED_FIELDS.contains(key) || NESTED_CONTAINER_FIELDS.contains(key)) {
                continue;
            }
            JsonNode value = entry.getValue();
            if (value == null || value.isNull()) {
                continue;
            }
            if (key.matches("\\d{5,}")) {
                Long idFromKey = Long.parseLong(key);
                collectRows(value, idFromKey, inheritedDate, fallbackDateFrom, fallbackDateTo, out);
                continue;
            }
            LocalDate dateFromKey = parseDateKey(key);
            if (dateFromKey != null) {
                collectRows(value, contextCampaignId, dateFromKey, fallbackDateFrom, fallbackDateTo, out);
            }
        }
    }

    private static boolean tryAddRow(
            JsonNode node,
            Long inheritedCampaignId,
            LocalDate inheritedDate,
            LocalDate fallbackDateFrom,
            LocalDate fallbackDateTo,
            List<Row> out
    ) {
        if (node == null || !node.isObject()) {
            return false;
        }
        Row row = toRow(node, inheritedCampaignId, inheritedDate, fallbackDateFrom, fallbackDateTo);
        if (row != null && !row.isTotalRow()) {
            out.add(row);
            return true;
        }
        return false;
    }

    private static Row toRowFromArray(
            JsonNode arrayNode,
            Long inheritedCampaignId,
            LocalDate inheritedDate,
            LocalDate fallbackDateTo
    ) {
        if (arrayNode == null || !arrayNode.isArray() || arrayNode.size() < 2) {
            return null;
        }
        String phrase = arrayNode.get(1).asText(null);
        if (phrase == null || phrase.isBlank()) {
            return null;
        }
        LocalDate date = inheritedDate;
        if (date == null && arrayNode.get(0).isTextual()) {
            date = readDateValue(arrayNode.get(0).asText());
        }
        if (date == null) {
            date = fallbackDateTo;
        }
        if (date == null) {
            return null;
        }
        Row row = new Row();
        row.setCampaignId(inheritedCampaignId);
        row.setSearchPhrase(phrase.trim());
        row.setDate(date);
        if (arrayNode.size() > 2) {
            row.setViews(readInteger(arrayNode.get(2)));
        }
        if (arrayNode.size() > 3) {
            row.setClicks(readInteger(arrayNode.get(3)));
        }
        return row;
    }

    private static Row toRow(
            JsonNode node,
            Long inheritedCampaignId,
            LocalDate inheritedDate,
            LocalDate fallbackDateFrom,
            LocalDate fallbackDateTo
    ) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String phrase = readPhrase(node);
        if (phrase == null || phrase.isBlank()) {
            return null;
        }
        Long campaignId = readLong(node, "campaignId", "campaign_id");
        if (campaignId == null) {
            campaignId = inheritedCampaignId;
        }
        LocalDate date = readDate(node, "date", "Date", "day", "statDate", "stat_date");
        if (date == null) {
            date = inheritedDate;
        }
        if (date == null) {
            date = fallbackDateTo != null ? fallbackDateTo : fallbackDateFrom;
        }
        if (date == null) {
            return null;
        }
        Row row = new Row();
        row.setCampaignId(campaignId);
        row.setSearchPhrase(phrase.trim());
        row.setDate(date);
        row.setSku(readLong(node, "sku", "SKU", "objectId", "object_id"));
        row.setAvgPos(readDecimal(node, "avgPos", "avg_pos", "averagePosition", "position"));
        row.setViews(readInteger(node, "views", "Views"));
        row.setClicks(readInteger(node, "clicks", "Clicks"));
        row.setCtr(readDecimal(node, "ctr", "CTR"));
        row.setToCart(readInteger(node, "toCart", "to_cart", "atbs"));
        row.setAvgCpc(readDecimal(node, "avgCpc", "avg_cpc", "cpc"));
        row.setSpend(readDecimal(node, "expense", "spend", "moneySpent", "money_spent"));
        row.setOrders(readInteger(node, "orders", "Orders"));
        return row;
    }

    private static String readPhrase(JsonNode node) {
        String direct = readText(node,
                "searchPhrase", "search_phrase", "phrase", "query", "normQuery", "norm_query",
                "searchQuery", "search_query", "criteria", "keyword", "cluster", "name", "title");
        if (direct != null) {
            return direct;
        }
        return findLongestTextField(node);
    }

    private static String findLongestTextField(JsonNode node) {
        Set<String> skipFields = Set.of(
                "date", "Date", "day", "statDate", "stat_date", "sku", "SKU", "campaignId", "campaign_id",
                "id", "objectId", "object_id", "state", "type", "uuid", "UUID"
        );
        String longest = null;
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (skipFields.contains(entry.getKey())) {
                continue;
            }
            JsonNode value = entry.getValue();
            if (value == null || !value.isTextual()) {
                continue;
            }
            String text = value.asText().trim();
            if (text.length() < 2 || text.matches("\\d+") || text.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
                continue;
            }
            if (longest == null || text.length() > longest.length()) {
                longest = text;
            }
        }
        return longest;
    }

    private static LocalDate parseDateKey(String key) {
        if (key == null || key.length() < 10) {
            return null;
        }
        if (!key.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
            return null;
        }
        return readDateValue(key.substring(0, 10));
    }

    private static LocalDate readDateValue(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.length() >= 10) {
            trimmed = trimmed.substring(0, 10);
        }
        try {
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static void collectAggressiveFallback(
            JsonNode node,
            Long inheritedCampaignId,
            LocalDate inheritedDate,
            LocalDate fallbackDateFrom,
            LocalDate fallbackDateTo,
            List<Row> out
    ) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(element -> collectAggressiveFallback(
                    element, inheritedCampaignId, inheritedDate, fallbackDateFrom, fallbackDateTo, out));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        if (hasMetricFields(node)) {
            tryAddRow(node, inheritedCampaignId, inheritedDate, fallbackDateFrom, fallbackDateTo, out);
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            Long campaignId = inheritedCampaignId;
            if (key.matches("\\d{5,}")) {
                campaignId = Long.parseLong(key);
            }
            LocalDate date = inheritedDate;
            LocalDate dateFromKey = parseDateKey(key);
            if (dateFromKey != null) {
                date = dateFromKey;
            }
            collectAggressiveFallback(value, campaignId, date, fallbackDateFrom, fallbackDateTo, out);
        }
    }

    private static boolean hasMetricFields(JsonNode node) {
        return node.has("views") || node.has("clicks") || node.has("expense")
                || node.has("moneySpent") || node.has("orders") || node.has("ctr")
                || node.has("toCart") || node.has("avgCpc");
    }

    private static void logSampleStructure(JsonNode node, int depth) {
        if (node == null || depth > 3) {
            return;
        }
        if (node.isObject()) {
            List<String> keys = new ArrayList<>();
            node.fieldNames().forEachRemaining(keys::add);
            log.warn("Ozon search phrases JSON: depth={}, keys={}", depth, keys);
            for (String key : keys) {
                JsonNode child = node.get(key);
                if (child != null && (child.isObject() || child.isArray())) {
                    logSampleStructure(child, depth + 1);
                }
            }
        } else if (node.isArray() && node.size() > 0) {
            log.warn("Ozon search phrases JSON: depth={}, arraySize={}, firstType={}",
                    depth, node.size(), node.get(0).getNodeType());
        }
    }

    private static String readText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isTextual() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private static Long readLong(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isNumber()) {
                return value.longValue();
            }
            if (value.isTextual()) {
                try {
                    return Long.parseLong(value.asText().trim());
                } catch (NumberFormatException ignored) {
                    // next
                }
            }
        }
        return null;
    }

    private static Integer readInteger(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            Integer parsed = readInteger(value);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private static Integer readInteger(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.intValue();
        }
        if (value.isTextual()) {
            String text = value.asText().trim().replace(',', '.');
            if (text.isEmpty()) {
                return null;
            }
            try {
                return (int) Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static BigDecimal readDecimal(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isNumber()) {
                return value.decimalValue();
            }
            if (value.isTextual()) {
                String text = value.asText().trim().replace(" ", "").replace(',', '.');
                if (text.isEmpty()) {
                    continue;
                }
                try {
                    return new BigDecimal(text);
                } catch (NumberFormatException ignored) {
                    // next
                }
            }
        }
        return null;
    }

    private static LocalDate readDate(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isTextual()) {
                LocalDate parsed = readDateValue(value.asText());
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return null;
    }

    /**
     * Строка статистики по поисковому запросу за день.
     */
    @Getter
    @Setter
    public static class Row {
        private Long campaignId;
        private Long sku;
        private LocalDate date;
        private String searchPhrase;
        private BigDecimal avgPos;
        private Integer views;
        private Integer clicks;
        private BigDecimal ctr;
        private Integer toCart;
        private BigDecimal avgCpc;
        private BigDecimal spend;
        private Integer orders;

        /** Признак строки «Всего» в CSV. */
        public boolean isTotalRow() {
            if (searchPhrase == null) {
                return true;
            }
            String lower = searchPhrase.trim().toLowerCase(Locale.ROOT);
            return lower.startsWith("bcero") || lower.startsWith("всего") || lower.startsWith("total");
        }
    }
}
