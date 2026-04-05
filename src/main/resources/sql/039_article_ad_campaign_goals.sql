-- Цель рекламной кампании по артикулу в разрезе кабинета (редактируемое поле на странице артикула).

CREATE TABLE IF NOT EXISTS solution.article_ad_campaign_goals (
    id BIGSERIAL PRIMARY KEY,
    cabinet_id BIGINT NOT NULL,
    nm_id BIGINT NOT NULL,
    goal_text TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_article_ad_goal_cabinet_nm UNIQUE (cabinet_id, nm_id),
    CONSTRAINT fk_article_ad_goal_cabinet FOREIGN KEY (cabinet_id) REFERENCES solution.cabinets(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_article_ad_campaign_goals_nm ON solution.article_ad_campaign_goals(nm_id);

COMMENT ON TABLE solution.article_ad_campaign_goals IS 'Цель РК по артикулу (nm_id) в кабинете';
COMMENT ON COLUMN solution.article_ad_campaign_goals.cabinet_id IS 'Кабинет продавца';
COMMENT ON COLUMN solution.article_ad_campaign_goals.nm_id IS 'Артикул WB';
COMMENT ON COLUMN solution.article_ad_campaign_goals.goal_text IS 'Текст цели рекламной кампании';
