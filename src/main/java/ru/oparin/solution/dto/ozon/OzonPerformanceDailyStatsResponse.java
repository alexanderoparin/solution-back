package ru.oparin.solution.dto.ozon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Ответ GET /api/client/statistics/daily/json.
 * Ozon может вернуть массив строк или объект с массивом rows/list/data.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class OzonPerformanceDailyStatsResponse {

    private JsonNode root;

    /**
     * Разбирает сырое JSON-тело в плоский список строк статистики.
     */
    public static List<Row> parseRows(JsonNode root) {
        if (root == null || root.isNull()) {
            return List.of();
        }
        List<JsonNode> nodes = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(nodes::add);
        } else if (root.isObject()) {
            JsonNode nested = firstArrayChild(root, "rows", "list", "data", "items", "report");
            if (nested != null) {
                nested.forEach(nodes::add);
            } else {
                // Иногда ключи — id кампаний, значения — массивы дней.
                Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    JsonNode value = entry.getValue();
                    if (value != null && value.isArray()) {
                        value.forEach(nodes::add);
                    } else if (value != null && value.isObject() && looksLikeRow(value)) {
                        nodes.add(value);
                    }
                }
            }
        }
        List<Row> rows = new ArrayList<>(nodes.size());
        for (JsonNode node : nodes) {
            Row row = toRow(node);
            if (row != null) {
                rows.add(row);
            }
        }
        return rows;
    }

    private static JsonNode firstArrayChild(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode child = root.get(name);
            if (child != null && child.isArray()) {
                return child;
            }
        }
        return null;
    }

    private static boolean looksLikeRow(JsonNode node) {
        return node.has("date") || node.has("Date")
                || node.has("views") || node.has("Views")
                || node.has("clicks") || node.has("Clicks");
    }

    private static Row toRow(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        Long campaignId = readLong(node, "id", "campaignId", "campaign_id", "Id");
        LocalDate date = readDate(node, "date", "Date", "day");
        if (campaignId == null || date == null) {
            return null;
        }
        Row row = new Row();
        row.setCampaignId(campaignId);
        row.setTitle(readText(node, "title", "Title", "name", "Name"));
        row.setDate(date);
        row.setViews(readInteger(node, "views", "Views", "impressions"));
        row.setClicks(readInteger(node, "clicks", "Clicks"));
        row.setSpend(readMoney(node, "moneySpent", "MoneySpent", "spend", "Spend", "expense"));
        row.setAvgBid(readMoney(node, "avgBid", "AvgBid", "averageBid", "bid"));
        row.setOrders(readInteger(node, "orders", "Orders", "orderedUnits"));
        row.setOrdersMoney(readMoney(node, "ordersMoney", "OrdersMoney", "orderMoney", "revenue"));
        return row;
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

    private static BigDecimal readMoney(JsonNode node, String... names) {
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

    private static String readText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode v = node.get(name);
            if (v != null && v.isTextual() && !v.asText().isBlank()) {
                return v.asText().trim();
            }
        }
        return null;
    }

    /**
     * Одна строка дневной статистики.
     */
    @Getter
    @Setter
    public static class Row {
        private Long campaignId;
        private String title;
        private LocalDate date;
        private Integer views;
        private Integer clicks;
        private BigDecimal spend;
        private BigDecimal avgBid;
        private Integer orders;
        private BigDecimal ordersMoney;
    }
}
