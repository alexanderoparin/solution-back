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
    /** Включена ли глобальная оплата сайта. */
    private boolean billingEnabled;

    /** Включена ли подписка на «Управление РК». */
    private boolean campaignManagementEnabled;
}

