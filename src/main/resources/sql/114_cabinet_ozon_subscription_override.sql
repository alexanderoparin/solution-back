-- Ручная настройка тарифа Ozon (seller/info отдаёт PREMIUM всем кабинетам без type_).

ALTER TABLE solution.cabinet_sync_state
    ADD COLUMN IF NOT EXISTS ozon_subscription_type_override VARCHAR(32);

COMMENT ON COLUMN solution.cabinet_sync_state.ozon_subscription_type_override IS
    'Ручной тариф Ozon для UI: UNSPECIFIED, PREMIUM, PREMIUM_PLUS и т.д.; NULL — автоопределение';
