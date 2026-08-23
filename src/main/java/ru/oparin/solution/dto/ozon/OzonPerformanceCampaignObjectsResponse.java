package ru.oparin.solution.dto.ozon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Ответы Performance API со списком SKU/объектов кампании.
 */
public final class OzonPerformanceCampaignObjectsResponse {

    private OzonPerformanceCampaignObjectsResponse() {
    }

    /**
     * Извлекает SKU из ответа /v2/products или /objects.
     */
    public static List<Long> parseSkus(JsonNode root) {
        if (root == null || root.isNull()) {
            return List.of();
        }
        List<Long> skus = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(node -> addSku(skus, node));
            return skus.stream().distinct().toList();
        }
        JsonNode products = root.get("products");
        if (products != null && products.isArray()) {
            products.forEach(node -> addSku(skus, node));
        }
        JsonNode list = root.get("list");
        if (list != null && list.isArray()) {
            list.forEach(node -> addSku(skus, node));
        }
        return skus.stream().distinct().toList();
    }

    private static void addSku(List<Long> skus, JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isNumber()) {
            skus.add(node.longValue());
            return;
        }
        if (node.isTextual()) {
            Long parsed = parseLong(node.asText());
            if (parsed != null) {
                skus.add(parsed);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        Long sku = readLong(node, "sku", "id", "SKU", "Sku");
        if (sku != null) {
            skus.add(sku);
        }
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
                Long parsed = parseLong(v.asText());
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return null;
    }

    private static Long parseLong(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * DTO продукта из /v2/products (для логов/отладки).
     */
    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Product {
        private String sku;
        private String title;
    }
}
