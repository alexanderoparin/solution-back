-- Добавление cabinet_id в product_stocks, product_card_analytics, product_price_history, product_barcodes.
-- Выполнять после 017 (схема solution). Заполнение: cabinet_id из product_cards по nm_id.

-- 1. product_barcodes
ALTER TABLE solution.product_barcodes
    ADD COLUMN IF NOT EXISTS cabinet_id BIGINT REFERENCES solution.cabinets(id) ON DELETE CASCADE;

UPDATE solution.product_barcodes pb
SET cabinet_id = (SELECT pc.cabinet_id FROM solution.product_cards pc WHERE pc.nm_id = pb.nm_id LIMIT 1)
WHERE cabinet_id IS NULL;

ALTER TABLE solution.product_barcodes
    ALTER COLUMN cabinet_id SET NOT NULL;

-- 2. product_stocks
ALTER TABLE solution.product_stocks
    ADD COLUMN IF NOT EXISTS cabinet_id BIGINT REFERENCES solution.cabinets(id) ON DELETE CASCADE;

UPDATE solution.product_stocks ps
SET cabinet_id = (SELECT pc.cabinet_id FROM solution.product_cards pc WHERE pc.nm_id = ps.nm_id LIMIT 1)
WHERE cabinet_id IS NULL;

ALTER TABLE solution.product_stocks
    ALTER COLUMN cabinet_id SET NOT NULL;

-- Уникальность по кабинету
ALTER TABLE solution.product_stocks
    DROP CONSTRAINT IF EXISTS uk_product_stocks_nm_warehouse_barcode;
CREATE UNIQUE INDEX IF NOT EXISTS idx_product_stocks_cabinet_nm_wh_barcode
    ON solution.product_stocks(cabinet_id, nm_id, warehouse_id, barcode);

-- 3. product_card_analytics
ALTER TABLE solution.product_card_analytics
    ADD COLUMN IF NOT EXISTS cabinet_id BIGINT REFERENCES solution.cabinets(id) ON DELETE CASCADE;

UPDATE solution.product_card_analytics pca
SET cabinet_id = (SELECT pc.cabinet_id FROM solution.product_cards pc WHERE pc.nm_id = pca.nm_id LIMIT 1)
WHERE cabinet_id IS NULL;

ALTER TABLE solution.product_card_analytics
    ALTER COLUMN cabinet_id SET NOT NULL;

ALTER TABLE solution.product_card_analytics
    DROP CONSTRAINT IF EXISTS product_card_analytics_nm_id_date_key;
CREATE UNIQUE INDEX IF NOT EXISTS idx_product_card_analytics_cabinet_nm_date
    ON solution.product_card_analytics(cabinet_id, nm_id, date);

-- 4. product_price_history
ALTER TABLE solution.product_price_history
    ADD COLUMN IF NOT EXISTS cabinet_id BIGINT REFERENCES solution.cabinets(id) ON DELETE CASCADE;

UPDATE solution.product_price_history pph
SET cabinet_id = (SELECT pc.cabinet_id FROM solution.product_cards pc WHERE pc.nm_id = pph.nm_id LIMIT 1)
WHERE cabinet_id IS NULL;

ALTER TABLE solution.product_price_history
    ALTER COLUMN cabinet_id SET NOT NULL;

ALTER TABLE solution.product_price_history
    DROP CONSTRAINT IF EXISTS product_price_history_nm_id_date_size_id_key;
CREATE UNIQUE INDEX IF NOT EXISTS idx_product_price_history_cabinet_nm_date_size
    ON solution.product_price_history(cabinet_id, nm_id, date, size_id);
