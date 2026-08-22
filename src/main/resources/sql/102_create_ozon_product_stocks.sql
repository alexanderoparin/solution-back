-- Остатки товаров Ozon (FBO/FBS по типу склада из Seller API).

CREATE TABLE IF NOT EXISTS solution.ozon_product_stocks (
    id BIGSERIAL PRIMARY KEY,
    cabinet_id BIGINT NOT NULL REFERENCES solution.cabinets(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL,
    sku BIGINT,
    stock_type VARCHAR(32) NOT NULL,
    present INTEGER NOT NULL DEFAULT 0,
    reserved INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ozon_product_stocks UNIQUE (cabinet_id, product_id, sku, stock_type)
);

COMMENT ON TABLE solution.ozon_product_stocks IS 'Остатки Ozon по product_id и типу склада (fbo/fbs и т.д.).';
COMMENT ON COLUMN solution.ozon_product_stocks.stock_type IS 'Тип склада из Ozon API (поле type).';
COMMENT ON COLUMN solution.ozon_product_stocks.present IS 'Доступное количество.';
COMMENT ON COLUMN solution.ozon_product_stocks.reserved IS 'Зарезервированное количество.';

CREATE INDEX IF NOT EXISTS idx_ozon_product_stocks_cabinet_product
    ON solution.ozon_product_stocks (cabinet_id, product_id);
