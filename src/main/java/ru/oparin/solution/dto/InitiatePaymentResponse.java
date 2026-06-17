package ru.oparin.solution.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Ответ на инициацию оплаты: ссылка для redirect покупателя.
 */
@Getter
@Builder
public class InitiatePaymentResponse {

    private Long paymentId;
    private String paymentUrl;
}
