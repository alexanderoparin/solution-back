package ru.oparin.solution.service.ozon;

import java.util.List;

/**
 * Категории доступа Ozon API, по которым фиксируем статус в кабинете
 * (аналог {@link ru.oparin.solution.service.wb.WbApiCategory} для WB).
 */
public enum OzonApiCategory {

    /** Seller API: Client-Id + Api-Key. */
    SELLER("Seller API"),

    /** Performance API: client_id + client_secret (реклама). */
    PERFORMANCE("Performance"),

    /** Каталог товаров. */
    CATALOG("Каталог"),

    /** Цены. */
    PRICES("Цены"),

    /** Остатки. */
    STOCKS("Остатки"),

    /** Аналитика продаж. */
    ANALYTICS("Аналитика"),

    /** Рекламные кампании и статистика Performance. */
    PROMOTION("Реклама");

    private final String displayName;

    OzonApiCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Категории, которые показываем в колонке доступов.
     */
    public static List<OzonApiCategory> displayed() {
        return List.of(values());
    }
}
