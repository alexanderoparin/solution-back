package ru.oparin.solution.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Статус платежа для polling после возврата с платёжной страницы.
 */
@Getter
@Builder
public class PaymentStatusResponse {

    private Long paymentId;
    private String status;
    private LocalDateTime expiresAt;
}
