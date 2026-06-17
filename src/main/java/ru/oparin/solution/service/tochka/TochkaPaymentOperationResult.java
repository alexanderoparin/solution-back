package ru.oparin.solution.service.tochka;

import lombok.Builder;
import lombok.Getter;

/**
 * Ответ Точка.API на создание платёжной операции.
 */
@Getter
@Builder
public class TochkaPaymentOperationResult {

    private final String operationId;
    private final String paymentLink;
    private final String status;
}
