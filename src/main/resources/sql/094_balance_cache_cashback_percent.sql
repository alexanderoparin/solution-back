-- Процент списания промо-бонусов (cashbacks[].percent) из GET /adv/v1/balance.
-- Используется в POST /adv/v1/budget/deposit как cashback_percent (лимит доли от sum).

ALTER TABLE solution.wb_cabinet_promotion_balance_cache
    ADD COLUMN IF NOT EXISTS cashback_percent INTEGER;

COMMENT ON COLUMN solution.wb_cabinet_promotion_balance_cache.cashback_percent IS
    'Процент от суммы пополнения, который можно оплатить промо-бонусами (cashbacks[].percent)';
