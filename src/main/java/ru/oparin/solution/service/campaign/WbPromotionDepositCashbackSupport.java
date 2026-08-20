package ru.oparin.solution.service.campaign;

import lombok.extern.slf4j.Slf4j;
import ru.oparin.solution.dto.wb.WbPromotionBudgetDepositRequest;
import ru.oparin.solution.model.WbCabinetPromotionBalanceCache;

/**
 * Подстановка промо-бонусов ({@code cashback_sum}/{@code cashback_percent}) в deposit.
 * По swagger WB промо можно списать только вместе с источником 0 (счёт) или 1 (баланс),
 * не больше {@code percent}% от общей суммы пополнения.
 */
@Slf4j
public final class WbPromotionDepositCashbackSupport {

    private WbPromotionDepositCashbackSupport() {
    }

    /**
     * Дополняет запрос deposit промо-бонусами из кэша баланса, если источник 0/1 и есть промо.
     *
     * @param request тело deposit (sum/type уже заданы)
     * @param cache   кэш GET /adv/v1/balance (может быть null)
     */
    public static void applyFromCache(WbPromotionBudgetDepositRequest request, WbCabinetPromotionBalanceCache cache) {
        if (request == null || request.getSum() == null || request.getSum() <= 0) {
            return;
        }
        Integer type = request.getType();
        if (type == null || (type != 0 && type != 1)) {
            return;
        }
        if (cache == null) {
            return;
        }
        Integer available = cache.getCashbackRub();
        Integer percent = cache.getCashbackPercent();
        if (available == null || available <= 0 || percent == null || percent <= 0) {
            return;
        }

        int maxByPercent = (int) (request.getSum() * (long) percent / 100L);
        if (maxByPercent <= 0) {
            return;
        }
        int cashbackSum = Math.min(available, maxByPercent);
        if (cashbackSum <= 0) {
            return;
        }

        request.setCashbackSum(cashbackSum);
        request.setCashbackPercent(percent);
        log.debug(
                "Deposit с промо: sum={}, type={}, cashback_sum={}, cashback_percent={} (доступно промо={})",
                request.getSum(),
                type,
                cashbackSum,
                percent,
                available
        );
    }
}
