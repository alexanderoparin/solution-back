-- Миграция рейтинга на WB Analytics item-rating: убрать reviews_count и feedbacks-пайплайн.

DROP TABLE IF EXISTS solution.feedbacks_sync_page_checkpoint;
DROP TABLE IF EXISTS solution.feedbacks_sync_accumulator;
DROP TABLE IF EXISTS solution.feedbacks_sync_runs;

ALTER TABLE solution.product_cards
    DROP COLUMN IF EXISTS reviews_count;

ALTER TABLE solution.product_cards
    ADD COLUMN IF NOT EXISTS rating_synced_at TIMESTAMP;

COMMENT ON COLUMN solution.product_cards.rating IS
    'Рейтинг по отзывам WB (feedbackRating), шкала 1–5. Источник: Analytics API item-rating.';

COMMENT ON COLUMN solution.product_cards.rating_synced_at IS
    'Время последнего обновления рейтинга из item-rating (для финализации многошаговой синхронизации).';

DELETE FROM solution.cabinet_scope_status
WHERE category = 'FEEDBACKS_AND_QUESTIONS';
