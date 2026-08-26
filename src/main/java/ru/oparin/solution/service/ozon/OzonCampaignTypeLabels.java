package ru.oparin.solution.service.ozon;

import java.util.Locale;
import java.util.Map;

/**
 * Человекочитаемые названия типов рекламных кампаний Ozon Performance
 * ({@code advObjectType}, {@code paymentType} из GET /api/client/campaign).
 */
public final class OzonCampaignTypeLabels {

    /**
     * Тип объекта рекламы — константы Performance API.
     *
     * @see <a href="https://docs.ozon.ru/api/performance/">Ozon Performance API</a>
     */
    private static final Map<String, String> ADV_OBJECT_TYPE = Map.ofEntries(
            Map.entry("SKU", "Трафареты"),
            Map.entry("SEARCH_PROMO", "Оплата за заказ"),
            Map.entry("BANNER", "Медийная (баннер)"),
            Map.entry("VIDEO_BANNER", "Видеобаннер"),
            Map.entry("BRAND", "Бренд"),
            Map.entry("REF_BLOGGER", "Внешний трафик (блогеры)"),
            Map.entry("REF_VK", "Внешний трафик (VK)")
    );

    /** Модель оплаты (CPC, CPM, CPO). */
    private static final Map<String, String> PAYMENT_TYPE = Map.of(
            "CPC", "за клик",
            "CPM", "за показы",
            "CPO", "за заказ"
    );

    private OzonCampaignTypeLabels() {
    }

    /**
     * Формирует подпись типа РК для UI: «Трафареты (за клик)».
     *
     * @param advObjectType тип объекта ({@code SKU}, {@code REF_VK} и т.д.)
     * @param paymentType   модель оплаты ({@code CPC}, {@code CPO} и т.д.)
     * @return подпись или {@code null}, если оба параметра пустые
     */
    public static String format(String advObjectType, String paymentType) {
        String objectLabel = resolveAdvObjectLabel(advObjectType);
        String paymentLabel = resolvePaymentLabel(paymentType);
        if (objectLabel == null && paymentLabel == null) {
            return null;
        }
        if (objectLabel == null) {
            return paymentLabel;
        }
        if (paymentLabel == null) {
            return objectLabel;
        }
        return objectLabel + " (" + paymentLabel + ")";
    }

    private static String resolveAdvObjectLabel(String advObjectType) {
        if (advObjectType == null || advObjectType.isBlank()) {
            return null;
        }
        String key = advObjectType.trim().toUpperCase(Locale.ROOT);
        return ADV_OBJECT_TYPE.getOrDefault(key, humanizeConstant(key));
    }

    private static String resolvePaymentLabel(String paymentType) {
        if (paymentType == null || paymentType.isBlank()) {
            return null;
        }
        String key = paymentType.trim().toUpperCase(Locale.ROOT);
        return PAYMENT_TYPE.getOrDefault(key, key.toLowerCase(Locale.ROOT));
    }

    /** REF_UNKNOWN → «Ref unknown» для редких новых констант Ozon. */
    private static String humanizeConstant(String key) {
        String[] parts = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            String part = parts[i].toLowerCase(Locale.ROOT);
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }
}
