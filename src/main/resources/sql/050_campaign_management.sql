-- Управление рекламными кампаниями: автопополнение, расписание, состояние, журнал изменений.

CREATE TABLE IF NOT EXISTS solution.campaign_auto_budget_settings (
    campaign_id BIGINT NOT NULL,
    cabinet_id BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    top_up_amount INTEGER,
    source_type INTEGER,
    threshold_rub INTEGER,
    max_top_ups_per_day INTEGER,
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (campaign_id),
    CONSTRAINT fk_campaign_auto_budget_campaign FOREIGN KEY (campaign_id) REFERENCES solution.promotion_campaigns(advert_id) ON DELETE CASCADE,
    CONSTRAINT fk_campaign_auto_budget_cabinet FOREIGN KEY (cabinet_id) REFERENCES solution.cabinets(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_campaign_auto_budget_cabinet
    ON solution.campaign_auto_budget_settings(cabinet_id);

COMMENT ON TABLE solution.campaign_auto_budget_settings IS 'Настройки автопополнения бюджета РК';
COMMENT ON COLUMN solution.campaign_auto_budget_settings.campaign_id IS 'ID кампании (advert_id WB)';
COMMENT ON COLUMN solution.campaign_auto_budget_settings.cabinet_id IS 'Кабинет';
COMMENT ON COLUMN solution.campaign_auto_budget_settings.enabled IS 'Включено автопополнение';
COMMENT ON COLUMN solution.campaign_auto_budget_settings.top_up_amount IS 'Сумма пополнения, руб';
COMMENT ON COLUMN solution.campaign_auto_budget_settings.source_type IS 'Источник WB: 0 счёт, 1 баланс, 3 бонусы';
COMMENT ON COLUMN solution.campaign_auto_budget_settings.threshold_rub IS 'Порог бюджета для пополнения, руб';
COMMENT ON COLUMN solution.campaign_auto_budget_settings.max_top_ups_per_day IS 'Макс. пополнений в сутки';
COMMENT ON COLUMN solution.campaign_auto_budget_settings.locked IS 'Форма сохранена и заблокирована до «Редактировать»';

CREATE TABLE IF NOT EXISTS solution.campaign_schedule_slot (
    id BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL,
    cabinet_id BIGINT NOT NULL,
    day_of_week SMALLINT NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    budget_rub INTEGER NOT NULL,
    repeat_group_id UUID,
    repeat_mode VARCHAR(20) NOT NULL DEFAULT 'DAILY',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_campaign_schedule_slot_cabinet FOREIGN KEY (cabinet_id) REFERENCES solution.cabinets(id) ON DELETE CASCADE,
    CONSTRAINT chk_campaign_schedule_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT chk_campaign_schedule_time CHECK (end_time > start_time)
);

COMMENT ON TABLE solution.campaign_schedule_slot IS 'Слоты расписания запуска РК (день недели + время)';
COMMENT ON COLUMN solution.campaign_schedule_slot.day_of_week IS '1=Пн … 7=Вс';
COMMENT ON COLUMN solution.campaign_schedule_slot.end_time IS
    'Время окончания слота (не включается). 23:59 — конец суток';
COMMENT ON COLUMN solution.campaign_schedule_slot.budget_rub IS 'Лимит расхода за слот, руб';
COMMENT ON COLUMN solution.campaign_schedule_slot.repeat_group_id IS 'Группа слотов, созданных одним действием «повторять»';
COMMENT ON COLUMN solution.campaign_schedule_slot.repeat_mode IS 'DAILY, WEEKENDS, WEEKDAYS';

CREATE INDEX IF NOT EXISTS idx_campaign_schedule_slot_campaign
    ON solution.campaign_schedule_slot(campaign_id, cabinet_id);

CREATE TABLE IF NOT EXISTS solution.campaign_management_state (
    campaign_id BIGINT NOT NULL,
    cabinet_id BIGINT NOT NULL,
    manual_stopped BOOLEAN NOT NULL DEFAULT TRUE,
    schedule_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    active_slot_id BIGINT,
    budget_at_slot_start INTEGER,
    last_budget_total INTEGER,
    last_budget_checked_at TIMESTAMP,
    top_ups_today_count INTEGER NOT NULL DEFAULT 0,
    top_ups_today_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (campaign_id),
    CONSTRAINT fk_campaign_management_state_campaign FOREIGN KEY (campaign_id) REFERENCES solution.promotion_campaigns(advert_id) ON DELETE CASCADE,
    CONSTRAINT fk_campaign_management_state_cabinet FOREIGN KEY (cabinet_id) REFERENCES solution.cabinets(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_campaign_management_state_cabinet
    ON solution.campaign_management_state(cabinet_id);

COMMENT ON TABLE solution.campaign_management_state IS 'Операционное состояние управления РК';
COMMENT ON COLUMN solution.campaign_management_state.manual_stopped IS 'Пользователь нажал «Остановить»';
COMMENT ON COLUMN solution.campaign_management_state.budget_at_slot_start IS 'Снимок total бюджета при входе в слот';

CREATE TABLE IF NOT EXISTS solution.campaign_change_log (
    id BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL,
    cabinet_id BIGINT NOT NULL,
    user_id BIGINT,
    message TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_campaign_change_log_user FOREIGN KEY (user_id) REFERENCES solution.users(id) ON DELETE SET NULL,
    CONSTRAINT fk_campaign_change_log_cabinet FOREIGN KEY (cabinet_id) REFERENCES solution.cabinets(id) ON DELETE CASCADE
);

COMMENT ON TABLE solution.campaign_change_log IS 'Журнал изменений настроек и действий по РК';
COMMENT ON COLUMN solution.campaign_change_log.user_id IS 'NULL — действие Auto';

CREATE INDEX IF NOT EXISTS idx_campaign_change_log_campaign_created
    ON solution.campaign_change_log(campaign_id, cabinet_id, created_at DESC);
