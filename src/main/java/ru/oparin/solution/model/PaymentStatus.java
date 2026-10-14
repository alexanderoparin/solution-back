package ru.oparin.solution.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Статус платежа за подписку.
 * Значения мапятся на строковые значения в БД
 * (pending, success, failed, refunded, expired).
 */
@Getter
@RequiredArgsConstructor
public enum PaymentStatus {

    PENDING("pending"),
    SUCCESS("success"),
    FAILED("failed"),
    REFUNDED("refunded"),
    /** Автозакрытие неоплаченного счёта планировщиком (старше 6 часов, джоба в 06:00). */
    EXPIRED("expired");

    private final String dbValue;
}
