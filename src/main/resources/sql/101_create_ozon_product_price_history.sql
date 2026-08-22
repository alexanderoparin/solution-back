-- История цен товаров Ozon (снимок на дату, без SPP).

CREATE TABLE IF NOT EXISTS solution.ozon_product_price_history (
    id BIGSERIAL PRIMARY KEY,
    cabinet_id BIGINT NOT NULL REFERENCES solution.cabinets(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL,
    date DATE NOT NULL,
    price NUMERIC(12, 2) NOT NULL,
    old_price NUMERIC(12, 2),
    marketing_price NUMERIC(12, 2),
    min_price NUMERIC(12, 2),
    currency_code VARCHAR(8) NOT NULL DEFAULT 'RUB',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ozon_product_price_history UNIQUE (cabinet_id, product_id, date)
);

COMMENT ON TABLE solution.ozon_product_price_history IS 'История цен Ozon по product_id (снимок на дату sync, без SPP).';
COMMENT ON COLUMN solution.ozon_product_price_history.date IS 'Дата снимка (обычно вчера, как у WB price history).';
COMMENT ON COLUMN solution.ozon_product_price_history.price IS 'Текущая цена продавца.';
COMMENT ON COLUMN solution.ozon_product_price_history.old_price IS 'Зачёркнутая цена до скидки.';
COMMENT ON COLUMN solution.ozon_product_price_history.marketing_price IS 'Цена для покупателя с акциями Ozon.';

CREATE INDEX IF NOT EXISTS idx_ozon_product_price_history_cabinet_date
    ON solution.ozon_product_price_history (cabinet_id, date);
