-- Создание таблицы wb_warehouses
CREATE TABLE IF NOT EXISTS solution.wb_warehouses (
    id INTEGER PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Комментарии
COMMENT ON TABLE solution.wb_warehouses IS 'Список складов WB';
COMMENT ON COLUMN solution.wb_warehouses.id IS 'ID склада WB';
COMMENT ON COLUMN solution.wb_warehouses.name IS 'Название склада';
COMMENT ON COLUMN solution.wb_warehouses.address IS 'Адрес склада';
COMMENT ON COLUMN solution.wb_warehouses.created_at IS 'Дата создания записи в БД';
COMMENT ON COLUMN solution.wb_warehouses.updated_at IS 'Дата последнего обновления записи в БД';
