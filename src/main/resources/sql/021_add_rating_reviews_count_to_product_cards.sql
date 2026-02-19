-- Рейтинг и количество отзывов по товару (из API отзывов WB).
-- Выполнять после миграций схемы solution.

ALTER TABLE solution.product_cards
    ADD COLUMN IF NOT EXISTS rating NUMERIC(3,2);

ALTER TABLE solution.product_cards
    ADD COLUMN IF NOT EXISTS reviews_count INTEGER;

COMMENT ON COLUMN solution.product_cards.rating IS 'Средний рейтинг по обработанным отзывам WB (1–5)';
COMMENT ON COLUMN solution.product_cards.reviews_count IS 'Количество обработанных отзывов по товару';
