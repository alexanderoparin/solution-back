-- Сброс ложного PREMIUM от seller/info (type без type_): статус INCONCLUSIVE.
UPDATE solution.cabinet_sync_state css
SET ozon_subscription_type = 'INCONCLUSIVE'
WHERE css.ozon_subscription_type IN ('PREMIUM', 'PREMIUM_LITE')
  AND css.ozon_subscription_type_override IS NULL
  AND EXISTS (
      SELECT 1
      FROM solution.cabinets c
      WHERE c.id = css.cabinet_id
        AND c.marketplace_type = 'OZON'
  );

COMMENT ON COLUMN solution.cabinet_sync_state.ozon_subscription_type IS
    'Авто: seller/info (type_). INCONCLUSIVE — is_premium=true без type_, Premium в ЛК не подтверждён.';
