-- Лимит бюджета слота: не перезапускать РК до конца слота; учёт пополнений за слот.

ALTER TABLE solution.campaign_management_state
    ADD COLUMN IF NOT EXISTS slot_budget_exhausted_slot_id BIGINT,
    ADD COLUMN IF NOT EXISTS slot_top_ups_rub INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN solution.campaign_management_state.slot_budget_exhausted_slot_id IS
    'ID слота, для которого исчерпан лимит бюджета (РК не запускается до конца окна слота)';
COMMENT ON COLUMN solution.campaign_management_state.slot_top_ups_rub IS
    'Сумма автопополнений за текущий активный слот, руб (для расчёта расхода)';
