-- Контент-рейтинг карточек Ozon (Seller API POST /v1/product/rating-by-sku, шкала 0–100).

ALTER TABLE solution.ozon_product_cards
    ADD COLUMN IF NOT EXISTS content_rating NUMERIC(5, 2);

ALTER TABLE solution.ozon_product_cards
    ADD COLUMN IF NOT EXISTS content_rating_synced_at TIMESTAMP;

COMMENT ON COLUMN solution.ozon_product_cards.content_rating IS 'Контент-рейтинг Ozon (0–100) из /v1/product/rating-by-sku';
COMMENT ON COLUMN solution.ozon_product_cards.content_rating_synced_at IS 'Время последней успешной записи контент-рейтинга';
