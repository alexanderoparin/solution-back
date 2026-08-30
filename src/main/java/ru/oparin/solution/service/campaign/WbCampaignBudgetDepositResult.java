package ru.oparin.solution.service.campaign;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ru.oparin.solution.dto.wb.WbPromotionBudgetResponse;

/**
 * Результат deposit бюджета РК: ответ WB и признак отказа по промо-бонусам.
 */
@Getter
@RequiredArgsConstructor
public class WbCampaignBudgetDepositResult {

    private final WbPromotionBudgetResponse response;
    /** WB отклонил пополнение с промо; выполнен или будет выполнен повтор без cashback. */
    private final boolean promoRejectedByWb;
}
