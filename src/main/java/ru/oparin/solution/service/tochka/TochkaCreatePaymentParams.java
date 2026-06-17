package ru.oparin.solution.service.tochka;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Параметры создания платёжной ссылки с фискализацией в Точка.API.
 */
@Getter
@Builder
public class TochkaCreatePaymentParams {

    private final BigDecimal amount;
    private final String purpose;
    private final String paymentLinkId;
    private final String redirectUrl;
    private final String failRedirectUrl;
    private final String clientEmail;
    private final String itemName;
}
