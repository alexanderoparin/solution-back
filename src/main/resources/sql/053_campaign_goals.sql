-- Цель на рекламную кампанию в разрезе кабинета (редактируемое поле на странице статистики РК).

CREATE TABLE IF NOT EXISTS solution.campaign_goals (
    id BIGSERIAL PRIMARY KEY,
    cabinet_id BIGINT NOT NULL,
    campaign_id BIGINT NOT NULL,
    goal_text TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_campaign_goal_cabinet_campaign UNIQUE (cabinet_id, campaign_id),
    CONSTRAINT fk_campaign_goal_cabinet FOREIGN KEY (cabinet_id) REFERENCES solution.cabinets(id) ON DELETE CASCADE,
    CONSTRAINT fk_campaign_goal_campaign FOREIGN KEY (campaign_id) REFERENCES solution.promotion_campaigns(advert_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_campaign_goals_campaign ON solution.campaign_goals(campaign_id);

COMMENT ON TABLE solution.campaign_goals IS 'Цель на рекламную кампанию в кабинете';
COMMENT ON COLUMN solution.campaign_goals.cabinet_id IS 'Кабинет продавца';
COMMENT ON COLUMN solution.campaign_goals.campaign_id IS 'ID рекламной кампании (advert_id WB)';
COMMENT ON COLUMN solution.campaign_goals.goal_text IS 'Текст цели на рекламную кампанию';
