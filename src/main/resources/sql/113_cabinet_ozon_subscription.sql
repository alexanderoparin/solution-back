-- Тариф Ozon Seller (subscription из POST /v1/seller/info) и доступность воронки analytics/data.

ALTER TABLE solution.cabinet_sync_state
    ADD COLUMN IF NOT EXISTS ozon_subscription_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS ozon_subscription_is_premium BOOLEAN,
    ADD COLUMN IF NOT EXISTS ozon_analytics_funnel_available BOOLEAN,
    ADD COLUMN IF NOT EXISTS ozon_subscription_checked_at TIMESTAMP;

COMMENT ON COLUMN solution.cabinet_sync_state.ozon_subscription_type IS 'Тип подписки Ozon Seller API (UNSPECIFIED, PREMIUM, PREMIUM_PLUS и т.д.)';
COMMENT ON COLUMN solution.cabinet_sync_state.ozon_subscription_is_premium IS 'Флаг is_premium из seller/info';
COMMENT ON COLUMN solution.cabinet_sync_state.ozon_analytics_funnel_available IS 'Seller API /v1/analytics/data отдаёт расширенную воронку (hits_view_pdp и др.)';
COMMENT ON COLUMN solution.cabinet_sync_state.ozon_subscription_checked_at IS 'Когда последний раз опрашивали seller/info или probe analytics';
