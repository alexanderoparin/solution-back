package ru.oparin.solution.dto.ozon;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Разбор JSON/CSV-отчёта Performance API по поисковым запросам (POST /statistics/phrases).
 */
public final class OzonPerformanceSearchPhrasesResponse {

    private OzonPerformanceSearchPhrasesResponse() {
    }

    /**
     * Разбирает JSON-тело отчёта в плоский список строк по фразам.
     */
    public static List<Row> parseRows(JsonNode root) {
        if (root == null || root.isNull()) {
            return List.of();
        }
        List<JsonNode> nodes = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(nodes::add);
        } else if (root.isObject()) {
            JsonNode rows = root.get("rows");
            if (rows != null && rows.isArray()) {
                rows.forEach(nodes::add);
            } else {
                Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    JsonNode value = entry.getValue();
                    if (value != null && value.isArray()) {
                        value.forEach(nodes::add);
                    }
                }
            }
        }
        List<Row> result = new ArrayList<>(nodes.size());
        for (JsonNode node : nodes) {
            Row row = toRow(node);
            if (row != null) {
                result.add(row);
            }
        }
        return result;
    }

    private static Row toRow(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        Long campaignId = readLong(node, "campaignId", "campaign_id", "id");
        String phrase = readText(node, "searchPhrase", "search_phrase", "phrase", "query", "normQuery", "norm_query");
        if (phrase == null || phrase.isBlank()) {
            return null;
        }
        LocalDate date = readDate(node, "date", "Date", "day");
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
        row.setSpend(readDecimal(node, "expense", "spend", "moneySpent"));
        row.setOrders(readInteger(node, "orders", "Orders"));
        return row;
    }

    private static String readText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode v = node.get(name);
            if (v != null && v.isTextual() && !v.asText().isBlank()) {
                return v.asText();
            }
        }
        return null;
    }

    private static Long readLong(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode v = node.get(name);
            if (v == null || v.isNull()) {
                continue;
            }
            if (v.isNumber()) {
                return v.longValue();
            }
            if (v.isTextual()) {
                try {
                    return Long.parseLong(v.asText().trim());
                } catch (NumberFormatException ignored) {
                    // next
                }
            }
        }
        return null;
    }

    private static Integer readInteger(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode v = node.get(name);
            if (v == null || v.isNull()) {
                continue;
            }
            if (v.isNumber()) {
                return v.intValue();
            }
            if (v.isTextual()) {
                String text = v.asText().trim().replace(',', '.');
                if (text.isEmpty()) {
                    continue;
                }
                try {
                    return (int) Double.parseDouble(text);
                } catch (NumberFormatException ignored) {
                    // next
                }
            }
        }
        return null;
    }

    private static BigDecimal readDecimal(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode v = node.get(name);
            if (v == null || v.isNull()) {
                continue;
            }
            if (v.isNumber()) {
                return v.decimalValue();
            }
            if (v.isTextual()) {
                String text = v.asText().trim().replace(" ", "").replace(',', '.');
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
            JsonNode v = node.get(name);
            if (v == null || v.isNull() || !v.isTextual()) {
                continue;
            }
            String text = v.asText().trim();
            if (text.length() >= 10) {
                text = text.substring(0, 10);
            }
            try {
                return LocalDate.parse(text);
            } catch (Exception ignored) {
                // next
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
