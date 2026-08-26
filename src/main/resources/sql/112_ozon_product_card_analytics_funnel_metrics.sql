-- Расширение воронки Ozon Seller API /v1/analytics/data (Premium-метрики могут быть null).

ALTER TABLE solution.ozon_product_card_analytics
    ADD COLUMN IF NOT EXISTS hits_view_pdp INTEGER,
    ADD COLUMN IF NOT EXISTS hits_tocart INTEGER,
    ADD COLUMN IF NOT EXISTS conv_tocart NUMERIC(10, 4);

COMMENT ON COLUMN solution.ozon_product_card_analytics.hits_view_pdp IS 'Просмотры карточки (hits_view_pdp), аналог переходов в карточку';
COMMENT ON COLUMN solution.ozon_product_card_analytics.hits_tocart IS 'Добавления в корзину (hits_tocart)';
COMMENT ON COLUMN solution.ozon_product_card_analytics.conv_tocart IS 'Конверсия в корзину, % (conv_tocart из Seller API /v1/analytics/data; NULL без Premium или если в ответе нет значения)';
