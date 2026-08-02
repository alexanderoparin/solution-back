-- Флаг «склад под ударом» (пожар / повреждения). Проставляется вручную; синк WB его не перезаписывает.

ALTER TABLE solution.wb_warehouses
    ADD COLUMN IF NOT EXISTS on_fire BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN solution.wb_warehouses.on_fire IS
    'Признак склада, пострадавшего от ЧС (пожар и т.п.): true — показывать огонёк в остатках';
