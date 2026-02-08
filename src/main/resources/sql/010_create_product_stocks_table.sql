-- Создание таблицы остатков товаров на складах WB
-- Данные перезаписываются каждый день, время записи фиксируется в created_at и updated_at

CREATE TABLE IF NOT EXISTS solution.product_stocks (
    id BIGSERIAL PRIMARY KEY,
    nm_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    barcode VARCHAR(255) NOT NULL,
    amount INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Уникальное ограничение: комбинация nm_id + warehouse_id + barcode
    CONSTRAINT uk_product_stocks_nm_warehouse_barcode UNIQUE (nm_id, warehouse_id, barcode),
    
    -- Внешние ключи
    CONSTRAINT fk_product_stocks_nm_id FOREIGN KEY (nm_id) REFERENCES solution.product_cards(nm_id) ON DELETE CASCADE,
    CONSTRAINT fk_product_stocks_barcode FOREIGN KEY (barcode) REFERENCES solution.product_barcodes(barcode) ON DELETE CASCADE,
    CONSTRAINT fk_product_stocks_warehouse_id FOREIGN KEY (warehouse_id) REFERENCES solution.wb_warehouses(id) ON DELETE RESTRICT
);

-- Комментарии к таблице и колонкам
COMMENT ON TABLE solution.product_stocks IS 'Остатки товаров на складах WB. Данные перезаписываются каждый день, время записи фиксируется в created_at и updated_at';
COMMENT ON COLUMN solution.product_stocks.id IS 'Уникальный идентификатор записи';
COMMENT ON COLUMN solution.product_stocks.nm_id IS 'Артикул WB (nmID)';
COMMENT ON COLUMN solution.product_stocks.warehouse_id IS 'ID склада WB';
COMMENT ON COLUMN solution.product_stocks.barcode IS 'Баркод товара (из product_barcodes)';
COMMENT ON COLUMN solution.product_stocks.amount IS 'Количество товара на складе';
COMMENT ON COLUMN solution.product_stocks.created_at IS 'Дата создания записи в БД (фиксирует время первой записи)';
COMMENT ON COLUMN solution.product_stocks.updated_at IS 'Дата последнего обновления записи в БД (фиксирует время последнего обновления)';
