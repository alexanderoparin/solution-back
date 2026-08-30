package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.oparin.solution.dto.wb.WbPromotionBudgetDepositRequest;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.service.wb.WbPromotionApiClient;

/**
 * Пополнение бюджета РК через WB с учётом промо-бонусов и откатом при устаревшем кэше.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WbCampaignBudgetDepositService {

    private final WbPromotionApiClient promotionApiClient;
    private final WbCabinetPromotionBalanceCacheService balanceCacheService;
    private final WbCampaignAutoBudgetPromoService autoBudgetPromoService;

    /**
     * Выполняет deposit; при ошибке промо (истёкший кэш / сгоревшие бонусы) повторяет без cashback.
     *
     * @param usePromoCashback подставлять {@code cashback_sum}/{@code cashback_percent} из кэша баланса
     * @param disableAutoPromoSettingOnRejection отключить {@code usePromoCashback} в автопополнении и записать в историю
     */
    public WbCampaignBudgetDepositResult depositWithPromoFallback(
            Cabinet cabinet,
            Long advertId,
            WbPromotionBudgetDepositRequest request,
            boolean usePromoCashback,
            boolean disableAutoPromoSettingOnRejection
    ) {
        if (usePromoCashback) {
            balanceCacheService.refreshBalanceIfStale(cabinet);
            WbPromotionDepositCashbackSupport.applyFromCache(
                    request,
                    balanceCacheService.findCache(cabinet.getId()).orElse(null)
            );
        }

        try {
            return new WbCampaignBudgetDepositResult(
                    promotionApiClient.depositCampaignBudget(cabinet.getApiKey(), advertId, request),
                    false
            );
        } catch (Exception firstError) {
            if (!usePromoCashback || request.getCashbackSum() == null || request.getCashbackSum() <= 0) {
                throw firstError;
            }
            if (!WbPromotionDepositCashbackSupport.isCashbackRelatedDepositError(firstError.getMessage())) {
                throw firstError;
            }
            log.info(
                    "Deposit advertId={} cabinetId={}: промо отклонено WB ({}), повтор без промо-бонусов",
                    advertId,
                    cabinet.getId(),
                    firstError.getMessage()
            );
            balanceCacheService.clearPromoCashback(cabinet.getId());
            if (disableAutoPromoSettingOnRejection) {
                autoBudgetPromoService.disableUsePromoCashbackAfterWbRejection(advertId, cabinet.getId());
            }
            WbPromotionDepositCashbackSupport.stripCashback(request);
            return new WbCampaignBudgetDepositResult(
                    promotionApiClient.depositCampaignBudget(cabinet.getApiKey(), advertId, request),
                    true
            );
        }
    }

    /**
     * Упрощённый вызов без изменения настроек автопополнения (ручное пополнение).
     */
    public WbCampaignBudgetDepositResult depositWithPromoFallback(
            Cabinet cabinet,
            Long advertId,
            WbPromotionBudgetDepositRequest request,
            boolean usePromoCashback
    ) {
        return depositWithPromoFallback(cabinet, advertId, request, usePromoCashback, false);
    }
}
