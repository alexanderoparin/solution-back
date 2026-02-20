package ru.oparin.solution.service.wb;

/**
 * Категории WB API по документации.
 * Доступ к методам зависит от выбранных категорий при создании токена.
 * См. <a href="https://dev.wildberries.ru/docs/openapi/api-information#tag/Avtorizaciya/Kak-sozdat-personalnyj-bazovyj-ili-testovyj-token">Как создать токен</a>.
 */
public enum WbApiCategory {
    /** Контент: карточки товаров, медиафайлы, ярлыки */
    CONTENT("Контент"),
    /** Аналитика: воронка продаж, поисковые запросы, остатки, отчёты */
    ANALYTICS("Аналитика"),
    /** Цены и скидки, календарь акций */
    PRICES_AND_DISCOUNTS("Цены и скидки"),
    /** Маркетплейс: заказы FBS/DBS, склады, остатки, самовывоз */
    MARKETPLACE("Маркетплейс"),
    /** Статистика: основные отчёты, финансовые отчёты, заказы (statistics-api) */
    STATISTICS("Статистика"),
    /** Продвижение: кампании, статистика кампаний */
    PROMOTION("Продвижение"),
    /** Вопросы и отзывы */
    FEEDBACKS_AND_QUESTIONS("Вопросы и отзывы"),
    /** Чат с покупателями */
    BUYER_CHAT("Чат с покупателями"),
    /** Поставки FBW */
    SUPPLIES("Поставки"),
    /** Возвраты покупателями */
    RETURNS("Возвраты покупателями"),
    /** Документы */
    DOCUMENTS("Документы"),
    /** Финансы, баланс */
    FINANCE("Финансы"),
    /** Управление пользователями продавца */
    USERS("Пользователи"),
    /** Тарифы, новости, информация о продавце (common-api) */
    COMMON("Тарифы, новости, информация о продавце");

    private final String displayName;

    WbApiCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
