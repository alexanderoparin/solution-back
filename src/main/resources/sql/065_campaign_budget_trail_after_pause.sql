-- Опрос бюджета WB ещё несколько минут после паузы РК (хвост trail).

ALTER TABLE solution.campaign_management_state
    ADD COLUMN IF NOT EXISTS budget_trail_until TIMESTAMP;

COMMENT ON COLUMN solution.campaign_management_state.budget_trail_until IS
    'До этого времени (МСК) продолжаем опрашивать бюджет WB после STOP, даже вне активного слота';
