-- Время последней проверки бюджета при блокировке запуска (повтор — не чаще раза в час в слоте).

ALTER TABLE solution.campaign_management_state
    ADD COLUMN IF NOT EXISTS start_no_budget_checked_at TIMESTAMP;

COMMENT ON COLUMN solution.campaign_management_state.start_no_budget_checked_at IS
    'Когда последний раз проверяли бюджет для запуска при отсутствии средств (МСК)';
