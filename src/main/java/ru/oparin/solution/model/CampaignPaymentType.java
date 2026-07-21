package ru.oparin.solution.model;

/**
 * Модель оплаты рекламной кампании Wildberries.
 */
public enum CampaignPaymentType {
    /** Оплата за показы. */
    CPM,

    /** Оплата за клики. */
    CPC;

    /**
     * Преобразует строковое значение WB API в тип оплаты.
     *
     * @param value значение поля {@code settings.payment_type}
     * @return тип оплаты или {@code null}, если WB не передал поддерживаемое значение
     */
    public static CampaignPaymentType fromApiValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return CampaignPaymentType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
