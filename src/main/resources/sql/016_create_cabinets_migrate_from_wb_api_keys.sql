-- Миграция: таблица кабинетов (объединение кабинета и WB API ключа).
-- Выполнить вручную ДО запуска приложения после обновления кода (схема solution).
-- После выполнения приложение будет использовать таблицу solution.cabinets вместо solution.wb_api_keys.

-- 1. Создать таблицу кабинетов
CREATE TABLE IF NOT EXISTS solution.cabinets (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES solution.users(id),
    name VARCHAR(255) NOT NULL,
    api_key VARCHAR(500),
    is_valid BOOLEAN,
    last_validated_at TIMESTAMP,
    validation_error TEXT,
    last_data_update_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_cabinets_user_id ON solution.cabinets(user_id);

-- 2. Перенести данные из wb_api_keys (один кабинет на пользователя)
INSERT INTO solution.cabinets (user_id, name, api_key, is_valid, last_validated_at, validation_error, last_data_update_at, created_at, updated_at)
SELECT user_id, 'Основной кабинет', api_key, is_valid, last_validated_at, validation_error, last_data_update_at, created_at, updated_at
FROM solution.wb_api_keys;

-- 3. Удалить старую таблицу ключей
DROP TABLE IF EXISTS solution.wb_api_keys;
