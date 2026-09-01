-- Сброс ложного is_premium=false от удалённого lookback-probe (seller/info отдаёт is_premium=true).
UPDATE solution.cabinet_sync_state css
SET ozon_subscription_is_premium = TRUE,
    ozon_subscription_type = 'PREMIUM'
WHERE css.ozon_subscription_is_premium = FALSE
  AND css.ozon_subscription_type_override IS NULL
  AND EXISTS (
      SELECT 1
      FROM solution.cabinets c
      JOIN solution.cabinet_integrations i
          ON i.cabinet_id = c.id
         AND i.integration_type = 'OZON_SELLER'
      WHERE c.id = css.cabinet_id
        AND c.marketplace_type = 'OZON'
  );

COMMENT ON COLUMN solution.cabinet_sync_state.ozon_subscription_type IS
    'Авто: seller/info (is_premium + type/type_). Lookback analytics probe отключён — ненадёжен.';
