package ru.oparin.solution.service.analytics;

import java.util.HashMap;
import java.util.Map;

/**
 * Константы с названиями метрик аналитики.
 */
public final class MetricNames {

    private MetricNames() {
        // Утилитный класс
    }

    // Английские ключи для метрик (используются в URL и API)
    public static final String TRANSITIONS = "transitions";
    public static final String CART = "cart";
    public static final String ORDERS = "orders";
    public static final String ORDERS_AMOUNT = "orders_amount";
    public static final String CART_CONVERSION = "cart_conversion";
    public static final String ORDER_CONVERSION = "order_conversion";
    public static final String VIEWS = "views";
    public static final String CLICKS = "clicks";
    public static final String COSTS = "costs";
    public static final String CPC = "cpc";
    public static final String CTR = "ctr";
    public static final String CPO = "cpo";
    public static final String DRR = "drr";

    // Русские названия для отображения
    public static final String TRANSITIONS_RU = "Переходы в карточку";
    public static final String CART_RU = "Положили в корзину, шт";
    public static final String ORDERS_RU = "Заказали товаров, шт";
    public static final String ORDERS_AMOUNT_RU = "Заказали на сумму, руб";
    public static final String CART_CONVERSION_RU = "Конверсия в корзину, %";
    public static final String ORDER_CONVERSION_RU = "Конверсия в заказ, %";
    public static final String VIEWS_RU = "Просмотры";
    public static final String CLICKS_RU = "Клики";
    public static final String COSTS_RU = "Затраты, руб";
    public static final String CPC_RU = "СРС, руб";
    public static final String CTR_RU = "CTR, %";
    public static final String CPO_RU = "СРО, руб";
    public static final String DRR_RU = "ДРР, %";

    // Маппинг английских ключей на русские названия
    private static final Map<String, String> RUSSIAN_NAMES = new HashMap<>();
    
    static {
        RUSSIAN_NAMES.put(TRANSITIONS, TRANSITIONS_RU);
        RUSSIAN_NAMES.put(CART, CART_RU);
        RUSSIAN_NAMES.put(ORDERS, ORDERS_RU);
        RUSSIAN_NAMES.put(ORDERS_AMOUNT, ORDERS_AMOUNT_RU);
        RUSSIAN_NAMES.put(CART_CONVERSION, CART_CONVERSION_RU);
        RUSSIAN_NAMES.put(ORDER_CONVERSION, ORDER_CONVERSION_RU);
        RUSSIAN_NAMES.put(VIEWS, VIEWS_RU);
        RUSSIAN_NAMES.put(CLICKS, CLICKS_RU);
        RUSSIAN_NAMES.put(COSTS, COSTS_RU);
        RUSSIAN_NAMES.put(CPC, CPC_RU);
        RUSSIAN_NAMES.put(CTR, CTR_RU);
        RUSSIAN_NAMES.put(CPO, CPO_RU);
        RUSSIAN_NAMES.put(DRR, DRR_RU);
    }

    /**
     * Возвращает массив всех метрик.
     */
    public static String[] getAllMetrics() {
        return new String[]{
                TRANSITIONS,
                CART,
                ORDERS,
                ORDERS_AMOUNT,
                CART_CONVERSION,
                ORDER_CONVERSION,
                VIEWS,
                CLICKS,
                COSTS,
                CPC,
                CTR,
                CPO,
                DRR
        };
    }

    /**
     * Проверяет, является ли метрика метрикой воронки.
     */
    public static boolean isFunnelMetric(String metricName) {
        return TRANSITIONS.equals(metricName)
                || CART.equals(metricName)
                || ORDERS.equals(metricName)
                || ORDERS_AMOUNT.equals(metricName)
                || CART_CONVERSION.equals(metricName)
                || ORDER_CONVERSION.equals(metricName);
    }

    /**
     * Проверяет, является ли метрика метрикой рекламы.
     */
    public static boolean isAdvertisingMetric(String metricName) {
        return VIEWS.equals(metricName)
                || CLICKS.equals(metricName)
                || COSTS.equals(metricName)
                || CPC.equals(metricName)
                || CTR.equals(metricName)
                || CPO.equals(metricName)
                || DRR.equals(metricName);
    }

    /**
     * Проверяет, является ли метрика процентной (измеряется в процентах).
     * Для процентных метрик изменение вычисляется как разница, а не как процентное изменение.
     */
    public static boolean isPercentageMetric(String metricName) {
        return CART_CONVERSION.equals(metricName)
                || ORDER_CONVERSION.equals(metricName)
                || CTR.equals(metricName)
                || DRR.equals(metricName);
    }

    /**
     * Возвращает русское название метрики по английскому ключу.
     *
     * @param metricKey английский ключ метрики
     * @return русское название или сам ключ, если название не найдено
     */
    public static String getRussianName(String metricKey) {
        return RUSSIAN_NAMES.getOrDefault(metricKey, metricKey);
    }

    /**
     * Возвращает английский ключ по русскому названию (для обратной совместимости).
     *
     * @param russianName русское название метрики
     * @return английский ключ или null, если не найдено
     */
    public static String getKeyByRussianName(String russianName) {
        return RUSSIAN_NAMES.entrySet().stream()
                .filter(entry -> entry.getValue().equals(russianName))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
}

