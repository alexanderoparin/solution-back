-- Сброс ложного PREMIUM от legacy seller/info и старого probe product-queries.
-- После деплоя тариф пересчитается автоматически (probe analytics lookback).

UPDATE solution.cabinet_sync_state
SET ozon_subscription_type = 'UNSPECIFIED',
    ozon_subscription_is_premium = false
WHERE ozon_subscription_type_override IS NULL
  AND ozon_subscription_type IN ('PREMIUM', 'PREMIUM_LITE');
