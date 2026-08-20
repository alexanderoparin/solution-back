-- Использовать промо-бонусы (cashbacks) при автопополнении бюджета РК.
-- false = deposit без cashback_sum / cashback_percent.

ALTER TABLE solution.wb_campaign_auto_budget_settings
    ADD COLUMN IF NOT EXISTS use_promo_cashback BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN solution.wb_campaign_auto_budget_settings.use_promo_cashback IS
    'Подставлять промо-бонусы (cashback_sum/percent) при автопополнении для источников счёт/баланс';
