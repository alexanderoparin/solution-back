-- Промо-бонусы (cashbacks) из GET /adv/v1/balance — отдельно от поля bonus (type 3).

ALTER TABLE solution.wb_cabinet_promotion_balance_cache
    ADD COLUMN IF NOT EXISTS cashback_rub INTEGER;

COMMENT ON COLUMN solution.wb_cabinet_promotion_balance_cache.cashback_rub IS
    'Сумма промо-бонусов (cashbacks[].sum) из GET /adv/v1/balance';
