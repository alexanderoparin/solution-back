CREATE TABLE IF NOT EXISTS solution.product_card_analytics (
    id BIGSERIAL PRIMARY KEY,
    nm_id BIGINT NOT NULL REFERENCES solution.product_cards(nm_id) ON DELETE CASCADE,
    date DATE NOT NULL,
    open_card INTEGER,
    add_to_cart INTEGER,
    orders INTEGER,
    orders_sum NUMERIC(19, 2),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(nm_id, date)
);

COMMENT ON TABLE solution.product_card_analytics IS 'Аналитика воронки продаж для карточек товаров';

COMMENT ON COLUMN solution.product_card_analytics.id IS 'Уникальный идентификатор записи';
COMMENT ON COLUMN solution.product_card_analytics.nm_id IS 'Идентификатор карточки товара (nmID из WB API)';
COMMENT ON COLUMN solution.product_card_analytics.date IS 'Дата аналитики';
COMMENT ON COLUMN solution.product_card_analytics.open_card IS 'Переходы в карточку';
COMMENT ON COLUMN solution.product_card_analytics.add_to_cart IS 'Положили в корзину, шт';
COMMENT ON COLUMN solution.product_card_analytics.orders IS 'Заказали товаров, шт';
COMMENT ON COLUMN solution.product_card_analytics.orders_sum IS 'Заказали на сумму, руб';
COMMENT ON COLUMN solution.product_card_analytics.created_at IS 'Дата создания записи';
COMMENT ON COLUMN solution.product_card_analytics.updated_at IS 'Дата последнего обновления записи';

