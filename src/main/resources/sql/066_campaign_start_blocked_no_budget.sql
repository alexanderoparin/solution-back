-- Блокировка повторных попыток запуска РК по расписанию при нулевом бюджете (до пополнения).

ALTER TABLE solution.campaign_management_state
    ADD COLUMN IF NOT EXISTS start_blocked_no_budget BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN solution.campaign_management_state.start_blocked_no_budget IS
    'Запуск по расписанию заблокирован: у РК нет бюджета для старта на WB';
