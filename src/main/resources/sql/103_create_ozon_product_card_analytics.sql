-- Ежедневная аналитика продаж товаров Ozon (базовые метрики Seller API /v1/analytics/data).

CREATE TABLE IF NOT EXISTS solution.ozon_product_card_analytics (
    id BIGSERIAL PRIMARY KEY,
    cabinet_id BIGINT NOT NULL REFERENCES solution.cabinets(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL,
    sku BIGINT,
    date DATE NOT NULL,
    ordered_units INTEGER,
    revenue NUMERIC(19, 2),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ozon_product_card_analytics_cabinet_product_date
        UNIQUE (cabinet_id, product_id, date)
);

COMMENT ON TABLE solution.ozon_product_card_analytics IS 'Ежедневная аналитика продаж Ozon (ordered_units, revenue) по product_id.';
COMMENT ON COLUMN solution.ozon_product_card_analytics.product_id IS 'Идентификатор товара Ozon (product_id)';
COMMENT ON COLUMN solution.ozon_product_card_analytics.sku IS 'SKU Ozon из измерения analytics/data';
COMMENT ON COLUMN solution.ozon_product_card_analytics.date IS 'Дата метрик';
COMMENT ON COLUMN solution.ozon_product_card_analytics.ordered_units IS 'Заказано единиц';
COMMENT ON COLUMN solution.ozon_product_card_analytics.revenue IS 'Выручка, руб.';

CREATE INDEX IF NOT EXISTS idx_ozon_product_card_analytics_cabinet_date
    ON solution.ozon_product_card_analytics (cabinet_id, date);

CREATE INDEX IF NOT EXISTS idx_ozon_product_card_analytics_cabinet_product
    ON solution.ozon_product_card_analytics (cabinet_id, product_id);
