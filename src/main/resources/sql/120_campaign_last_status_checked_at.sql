-- Время последней сверки статуса РК с WB (GET /api/advert/v2/adverts) в активном слоте.
ALTER TABLE solution.wb_campaign_management_state
    ADD COLUMN IF NOT EXISTS last_status_checked_at TIMESTAMP;

COMMENT ON COLUMN solution.wb_campaign_management_state.last_status_checked_at IS
    'МСК: последняя сверка статуса РК с WB в активном слоте (чтобы ловить паузу на стороне WB)';
