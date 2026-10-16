-- Запасной источник автопополнения бюджета РК (nullable = не использовать).
ALTER TABLE solution.wb_campaign_auto_budget_settings
    ADD COLUMN IF NOT EXISTS backup_source_type INTEGER;

COMMENT ON COLUMN solution.wb_campaign_auto_budget_settings.backup_source_type IS
    'Запасной источник WB при ошибке основного: 0 счёт, 1 баланс, 3 бонусы; NULL — не использовать';
