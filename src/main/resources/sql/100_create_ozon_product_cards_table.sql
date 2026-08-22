-- Каталог товаров Ozon-кабинета (read-only sync).

CREATE TABLE IF NOT EXISTS solution.ozon_product_cards (
    id BIGSERIAL PRIMARY KEY,
    cabinet_id BIGINT NOT NULL REFERENCES solution.cabinets(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL,
    offer_id VARCHAR(255),
    sku BIGINT,
    title VARCHAR(500),
    photo_url VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ozon_product_cards_cabinet_product UNIQUE (cabinet_id, product_id)
);

COMMENT ON TABLE solution.ozon_product_cards IS 'Карточки товаров Ozon, синхронизированные из Seller API.';
COMMENT ON COLUMN solution.ozon_product_cards.product_id IS 'Идентификатор товара Ozon (product_id)';
COMMENT ON COLUMN solution.ozon_product_cards.offer_id IS 'Артикул продавца (offer_id)';
COMMENT ON COLUMN solution.ozon_product_cards.sku IS 'SKU Ozon';

CREATE INDEX IF NOT EXISTS idx_ozon_product_cards_cabinet_id
    ON solution.ozon_product_cards (cabinet_id);
