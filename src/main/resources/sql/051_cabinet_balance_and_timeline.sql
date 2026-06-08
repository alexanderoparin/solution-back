-- Кэш баланса продвижения WB по кабинету и временная шкала бюджета РК для биддер-графика.

CREATE TABLE IF NOT EXISTS solution.cabinet_promotion_balance_cache (
    cabinet_id BIGINT NOT NULL PRIMARY KEY,
    balance_rub INTEGER,
    net_rub INTEGER,
    bonus_rub INTEGER,
    fetched_at TIMESTAMP,
    fetch_error TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cabinet_promotion_balance_cache_cabinet FOREIGN KEY (cabinet_id) REFERENCES solution.cabinets(id) ON DELETE CASCADE
);

COMMENT ON TABLE solution.cabinet_promotion_balance_cache IS 'Кэш баланса продвижения WB (GET /adv/v1/balance) по кабинету';
COMMENT ON COLUMN solution.cabinet_promotion_balance_cache.balance_rub IS 'Счёт (type 0)';
COMMENT ON COLUMN solution.cabinet_promotion_balance_cache.net_rub IS 'Баланс (type 1)';
COMMENT ON COLUMN solution.cabinet_promotion_balance_cache.bonus_rub IS 'Бонусы (type 3)';
COMMENT ON COLUMN solution.cabinet_promotion_balance_cache.fetched_at IS 'Время последнего успешного ответа WB';

CREATE TABLE IF NOT EXISTS solution.campaign_budget_timeline (
    id BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL,
    cabinet_id BIGINT NOT NULL,
    recorded_at TIMESTAMP NOT NULL,
    event_type VARCHAR(16) NOT NULL,
    budget_total INTEGER,
    top_up_amount INTEGER,
    CONSTRAINT fk_campaign_budget_timeline_cabinet FOREIGN KEY (cabinet_id) REFERENCES solution.cabinets(id) ON DELETE CASCADE,
    CONSTRAINT chk_campaign_budget_timeline_event_type CHECK (event_type IN ('SNAPSHOT', 'TOP_UP', 'START', 'STOP'))
);

CREATE INDEX IF NOT EXISTS idx_campaign_budget_timeline_campaign_recorded
    ON solution.campaign_budget_timeline(campaign_id, cabinet_id, recorded_at);

COMMENT ON TABLE solution.campaign_budget_timeline IS 'События и снимки бюджета РК для графика';
COMMENT ON COLUMN solution.campaign_budget_timeline.event_type IS 'SNAPSHOT — снимок бюджета; TOP_UP — пополнение; START/STOP — запуск/пауза';
COMMENT ON COLUMN solution.campaign_budget_timeline.budget_total IS 'Остаток бюджета РК после события (для SNAPSHOT/TOP_UP)';
COMMENT ON COLUMN solution.campaign_budget_timeline.top_up_amount IS 'Сумма пополнения (для TOP_UP)';
