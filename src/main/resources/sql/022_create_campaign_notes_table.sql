-- Заметки к рекламным кампаниям (РК).
-- campaign_id = advert_id из promotion_campaigns, cabinet_id для области видимости.

CREATE TABLE IF NOT EXISTS solution.campaign_notes (
    id BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL,
    cabinet_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_campaign_notes_user FOREIGN KEY (user_id) REFERENCES solution.users(id) ON DELETE CASCADE,
    CONSTRAINT fk_campaign_notes_cabinet FOREIGN KEY (cabinet_id) REFERENCES solution.cabinets(id) ON DELETE CASCADE
);

COMMENT ON TABLE solution.campaign_notes IS 'Заметки к рекламным кампаниям (РК)';
COMMENT ON COLUMN solution.campaign_notes.campaign_id IS 'ID кампании (advert_id из WB)';
COMMENT ON COLUMN solution.campaign_notes.cabinet_id IS 'Кабинет, к которому привязана кампания';
COMMENT ON COLUMN solution.campaign_notes.user_id IS 'Пользователь, создавший заметку';
COMMENT ON COLUMN solution.campaign_notes.content IS 'Текст заметки';

CREATE INDEX IF NOT EXISTS idx_campaign_notes_campaign_cabinet
    ON solution.campaign_notes(campaign_id, cabinet_id);
