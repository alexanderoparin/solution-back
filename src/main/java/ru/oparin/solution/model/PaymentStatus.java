package ru.oparin.solution.model;

/**
 * Статус платежа за подписку.
 * Значения мапятся на строковые значения в БД (pending, success, failed, refunded).
 */
public enum PaymentStatus {

    PENDING("pending"),
    SUCCESS("success"),
    FAILED("failed"),
    REFUNDED("refunded");

    private final String dbValue;

    PaymentStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static PaymentStatus fromDbValue(String value) {
        if (value == null) {
            return null;
        }
        for (PaymentStatus status : values()) {
            if (status.dbValue.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown payment status: " + value);
    }
}

