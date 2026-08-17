-- Склады продавца (Marketplace API /api/v3/warehouses) и остатки FBS
-- (/api/v3/stocks/{warehouseId}). Не смешивать с wb_warehouses / product_stocks (FBO).

CREATE TABLE IF NOT EXISTS solution.seller_warehouses (
    id BIGSERIAL PRIMARY KEY,
    cabinet_id BIGINT NOT NULL,
    -- ID склада продавца в WB (не officeId и не wb_warehouses.id)
    warehouse_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    -- ID офиса WB, к которому привязан склад продавца (другой справочник, чем wb_warehouses)
    office_id BIGINT,
    -- 1 — МГТ, 2 — СГТ, 3 — КГТ+
    cargo_type INTEGER,
    -- 1 — FBS, 2 — DBS, 3 — DBW, 5 — C&C, 6 — EDBS
    delivery_type INTEGER,
    is_deleting BOOLEAN NOT NULL DEFAULT FALSE,
    is_processing BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_seller_warehouses_cabinet_wb UNIQUE (cabinet_id, warehouse_id),
    CONSTRAINT fk_seller_warehouses_cabinet FOREIGN KEY (cabinet_id)
        REFERENCES solution.cabinets (id) ON DELETE CASCADE
);

COMMENT ON TABLE solution.seller_warehouses IS
    'Склады продавца WB (FBS/DBS и др.) из Marketplace API. ID не пересекаются с wb_warehouses';
COMMENT ON COLUMN solution.seller_warehouses.id IS 'Внутренний идентификатор записи';
COMMENT ON COLUMN solution.seller_warehouses.cabinet_id IS 'Кабинет продавца';
COMMENT ON COLUMN solution.seller_warehouses.warehouse_id IS 'ID склада продавца в WB (seller warehouse id)';
COMMENT ON COLUMN solution.seller_warehouses.name IS 'Название склада продавца';
COMMENT ON COLUMN solution.seller_warehouses.office_id IS 'ID офиса WB для привязки склада (не wb_warehouses.id)';
COMMENT ON COLUMN solution.seller_warehouses.cargo_type IS 'Тип товара: 1 МГТ, 2 СГТ, 3 КГТ+';
COMMENT ON COLUMN solution.seller_warehouses.delivery_type IS 'Тип доставки: 1 FBS, 2 DBS, 3 DBW, 5 C&C, 6 EDBS';
COMMENT ON COLUMN solution.seller_warehouses.is_deleting IS 'Склад удаляется на стороне WB';
COMMENT ON COLUMN solution.seller_warehouses.is_processing IS 'Данные склада обновляются на стороне WB';
COMMENT ON COLUMN solution.seller_warehouses.created_at IS 'Дата создания записи в БД';
COMMENT ON COLUMN solution.seller_warehouses.updated_at IS 'Дата последнего обновления записи в БД';

CREATE TABLE IF NOT EXISTS solution.product_fbs_stocks (
    id BIGSERIAL PRIMARY KEY,
    cabinet_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    nm_id BIGINT,
    chrt_id BIGINT NOT NULL,
    sku VARCHAR(255),
    amount INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_product_fbs_stocks_cabinet_wh_chrt UNIQUE (cabinet_id, warehouse_id, chrt_id),
    CONSTRAINT fk_product_fbs_stocks_cabinet FOREIGN KEY (cabinet_id)
        REFERENCES solution.cabinets (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_fbs_stocks_warehouse FOREIGN KEY (cabinet_id, warehouse_id)
        REFERENCES solution.seller_warehouses (cabinet_id, warehouse_id) ON DELETE CASCADE
);

COMMENT ON TABLE solution.product_fbs_stocks IS
    'Остатки товаров на складах продавца (FBS). Снимок по chrtId; не смешивать с product_stocks (FBO)';
COMMENT ON COLUMN solution.product_fbs_stocks.id IS 'Уникальный идентификатор записи';
COMMENT ON COLUMN solution.product_fbs_stocks.cabinet_id IS 'Кабинет продавца';
COMMENT ON COLUMN solution.product_fbs_stocks.warehouse_id IS 'ID склада продавца в WB';
COMMENT ON COLUMN solution.product_fbs_stocks.nm_id IS 'Артикул WB (nmID) из product_barcodes, если известен';
COMMENT ON COLUMN solution.product_fbs_stocks.chrt_id IS 'ID размера товара (chrtId) в WB';
COMMENT ON COLUMN solution.product_fbs_stocks.sku IS 'Баркод (sku) из ответа WB или из product_barcodes';
COMMENT ON COLUMN solution.product_fbs_stocks.amount IS 'Количество товара на складе продавца';
COMMENT ON COLUMN solution.product_fbs_stocks.created_at IS 'Дата создания записи в БД';
COMMENT ON COLUMN solution.product_fbs_stocks.updated_at IS 'Дата последнего обновления записи в БД';
