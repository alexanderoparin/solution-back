package ru.oparin.solution.dto;

import lombok.*;

/**
 * DTO для ответа о статусе оплаты/тарифов.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionStatusResponse {
    /** Включена ли оплата (тарифы, Робокасса). */
    private boolean billingEnabled;
}

