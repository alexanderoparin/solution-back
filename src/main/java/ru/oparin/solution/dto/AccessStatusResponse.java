package ru.oparin.solution.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * DTO для ответа о статусе доступа пользователя к продукту.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessStatusResponse {

    private boolean hasAccess;

    private boolean agencyClient;

    /** Подтверждена ли почта (для обычных селлеров без доступа до подтверждения). */
    private boolean emailConfirmed;

    /** Включена ли оплата (тарифы, Робокасса). Фронт скрывает блок оплаты при false. */
    private boolean billingEnabled;

    private String subscriptionStatus;

    private LocalDateTime subscriptionExpiresAt;
}

