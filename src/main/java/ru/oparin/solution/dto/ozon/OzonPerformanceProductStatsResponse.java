package ru.oparin.solution.dto.ozon;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Разбор JSON/CSV-отчёта Performance API по SKU (поле rows или report.rows).
 */
@Slf4j
public final class OzonPerformanceProductStatsResponse {

    private static final Set<String> SKIP_NESTED_FIELDS = Set.of(
            "rows", "report", "campaigns", "meta", "request", "title", "name"
    );

    private OzonPerformanceProductStatsResponse() {
    }

    /**
     * Разбирает JSON-тело отчёта в плоский список строк по SKU.
     * Поддерживает {@code rows}, {@code report.rows}, {@code campaigns[]} и ключи-ID кампаний.
     */
    public static List<Row> parseRows(JsonNode root) {
        if (root == null || root.isNull()) {
            return List.of();
        }
        List<RowCandidate> candidates = new ArrayList<>();
        collectRowNodes(root, null, candidates);
        List<Row> result = new ArrayList<>(candidates.size());
        for (RowCandidate candidate : candidates) {
            Row row = toRow(candidate.node(), candidate.campaignId());
            if (row != null) {
                result.add(row);
            }
        }
        if (result.isEmpty() && root.isObject()) {
            List<String> keys = new ArrayList<>();
            root.fieldNames().forEachRemaining(keys::add);
            log.warn("Ozon product stats JSON: 0 строк, корневые ключи: {}", keys);
        } else {
            log.info("Ozon product stats JSON: распознано {} строк SKU", result.size());
        }
        return result;
    }

    private static void collectRowNodes(JsonNode node, Long inheritedCampaignId, List<RowCandidate> out) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(element -> collectRowNodes(element, inheritedCampaignId, out));
            return;
        }
        if (!node.isObject()) {
            return;
        }

        if (looksLikeDataRow(node)) {
            out.add(new RowCandidate(node, inheritedCampaignId));
            return;
        }

        Long campaignId = readLong(node, "campaignId", "campaign_id");
        if (campaignId == null) {
            campaignId = readLong(node, "id");
        }
        Long contextCampaignId = campaignId != null ? campaignId : inheritedCampaignId;

        JsonNode report = node.get("report");
        if (report != null) {
            collectRowNodes(report, contextCampaignId, out);
        }

        JsonNode rows = node.get("rows");
        if (rows != null && rows.isArray()) {
            rows.forEach(rowNode -> {
                if (rowNode != null && rowNode.isObject()) {
                    out.add(new RowCandidate(rowNode, contextCampaignId));
                }
            });
        }

        JsonNode campaigns = node.get("campaigns");
        if (campaigns != null) {
            collectRowNodes(campaigns, contextCampaignId, out);
        }

        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            if (SKIP_NESTED_FIELDS.contains(key)) {
                continue;
            }
            if (key.matches("\\d{5,}")) {
                Long idFromKey = Long.parseLong(key);
                collectRowNodes(entry.getValue(), idFromKey, out);
            }
        }
    }

    private static boolean looksLikeDataRow(JsonNode node) {
        Long sku = readLong(node, "sku", "SKU", "objectId", "object_id");
        if (sku == null) {
            return false;
        }
        if (readDate(node, "date", "Date", "day", "statDate", "stat_date") != null) {
            return true;
        }
        return node.has("views") || node.has("clicks") || node.has("expense") || node.has("moneySpent");
    }

    private static Row toRow(JsonNode node, Long inheritedCampaignId) {
        if (node == null || !node.isObject()) {
            return null;
        }
        Long campaignId = readLong(node, "campaignId", "campaign_id", "id");
        if (campaignId == null) {
            campaignId = inheritedCampaignId;
        }
        Long sku = readLong(node, "sku", "SKU", "objectId", "object_id");
        LocalDate date = readDate(node, "date", "Date", "day", "statDate", "stat_date");
        if (sku == null || date == null) {
            return null;
        }
        Row row = new Row();
        row.setCampaignId(campaignId);
        row.setSku(sku);
        row.setDate(date);
        row.setViews(readInteger(node, "views", "Views"));
        row.setClicks(readInteger(node, "clicks", "Clicks"));
        row.setCtr(readDecimal(node, "ctr", "CTR"));
        row.setToCart(readInteger(node, "toCart", "to_cart", "atbs"));
        row.setAvgCpc(readDecimal(node, "avgCpc", "avg_cpc", "cpc"));
        row.setSpend(readDecimal(node, "expense", "spend", "moneySpent"));
        row.setOrders(readInteger(node, "orders", "Orders"));
        row.setOrdersMoney(readDecimal(node, "sales", "ordersMoney", "revenue"));
        row.setModelOrders(readInteger(node, "modelOrders", "model_orders"));
        row.setModelSales(readDecimal(node, "modelSales", "model_sales"));
        row.setDrr(readDecimal(node, "drr", "DRR"));
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
                String text = v.asText().trim().replace(" ", "");
                if (text.isEmpty()) {
                    continue;
                }
                try {
                    return Long.parseLong(text);
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
            if (v == null || v.isNull()) {
                continue;
            }
            String text = null;
            if (v.isTextual()) {
                text = v.asText().trim();
            } else if (v.isNumber()) {
                text = v.asText();
            }
            if (text == null || text.isBlank()) {
                continue;
            }
            if (text.length() >= 10) {
                text = text.substring(0, 10);
            }
            try {
                return LocalDate.parse(text);
            } catch (Exception ignored) {
                if (text.length() >= 10 && text.charAt(2) == '.') {
                    try {
                        return LocalDate.parse(text.substring(0, 10),
                                java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                    } catch (Exception ignored2) {
                        // next
                    }
                }
            }
        }
        return null;
    }

    private record RowCandidate(JsonNode node, Long campaignId) {
    }

    /**
     * Строка статистики SKU в кампании за день.
     */
    @Getter
    @Setter
    public static class Row {
        private Long campaignId;
        private Long sku;
        private LocalDate date;
        private Integer views;
        private Integer clicks;
        private BigDecimal ctr;
        private Integer toCart;
        private BigDecimal avgCpc;
        private BigDecimal spend;
        private Integer orders;
        private BigDecimal ordersMoney;
        private Integer modelOrders;
        private BigDecimal modelSales;
        private BigDecimal drr;
    }
}
