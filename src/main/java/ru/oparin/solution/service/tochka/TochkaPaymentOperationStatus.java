package ru.oparin.solution.service.tochka;

import lombok.Builder;
import lombok.Getter;

/**
 * Статус платёжной операции в Точка.API.
 */
@Getter
@Builder
public class TochkaPaymentOperationStatus {

    private final String operationId;
    private final String status;
    private final String paymentLinkId;
}
