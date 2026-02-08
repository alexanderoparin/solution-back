-- Создание таблицы баркодов товаров
-- Баркоды сохраняются из информации о карточке товара (CardDto.Size.skus)
-- Баркод является уникальным ключом

CREATE TABLE IF NOT EXISTS solution.product_barcodes (
    barcode VARCHAR(255) PRIMARY KEY,
    nm_id BIGINT NOT NULL,
    chrt_id BIGINT NOT NULL,
    tech_size VARCHAR(50),
    wb_size VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Внешний ключ
    CONSTRAINT fk_product_barcodes_nm_id FOREIGN KEY (nm_id) REFERENCES solution.product_cards(nm_id) ON DELETE CASCADE
);

-- Комментарии к таблице и колонкам
COMMENT ON TABLE solution.product_barcodes IS 'Баркоды товаров. Сохраняются из информации о карточке товара (CardDto.Size.skus). Баркод является первичным ключом';
COMMENT ON COLUMN solution.product_barcodes.barcode IS 'Баркод товара (из массива skus в CardDto.Size). Первичный ключ';
COMMENT ON COLUMN solution.product_barcodes.nm_id IS 'Артикул WB (nmID)';
COMMENT ON COLUMN solution.product_barcodes.chrt_id IS 'ID характеристики размера (chrtID)';
COMMENT ON COLUMN solution.product_barcodes.tech_size IS 'Технический размер (techSize, например "L")';
COMMENT ON COLUMN solution.product_barcodes.wb_size IS 'Российский размер (wbSize, например "48")';
COMMENT ON COLUMN solution.product_barcodes.created_at IS 'Дата создания записи в БД';
COMMENT ON COLUMN solution.product_barcodes.updated_at IS 'Дата последнего обновления записи в БД';
