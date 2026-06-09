-- Переименование: «цель рекламной кампании» → «цель на артикул».

ALTER TABLE IF EXISTS solution.article_ad_campaign_goals RENAME TO article_goals;

ALTER INDEX IF EXISTS solution.idx_article_ad_campaign_goals_nm RENAME TO idx_article_goals_nm;

ALTER TABLE solution.article_goals
    RENAME CONSTRAINT uq_article_ad_goal_cabinet_nm TO uq_article_goal_cabinet_nm;

ALTER TABLE solution.article_goals
    RENAME CONSTRAINT fk_article_ad_goal_cabinet TO fk_article_goal_cabinet;

COMMENT ON TABLE solution.article_goals IS 'Цель на артикул (nm_id) в кабинете';
COMMENT ON COLUMN solution.article_goals.goal_text IS 'Текст цели на артикул';
