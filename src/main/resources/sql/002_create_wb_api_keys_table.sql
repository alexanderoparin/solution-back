-- Создание таблицы wb_api_keys
-- Связь 1:1 с users - у каждого SELLER'а только один WB API ключ со всеми правами
-- Через этот ключ ночью подтягиваются данные из Wildberries API
CREATE TABLE IF NOT EXISTS solution.wb_api_keys (
    user_id BIGINT PRIMARY KEY REFERENCES solution.users(id) ON DELETE CASCADE,
    api_key VARCHAR(500) NOT NULL,
    is_valid BOOLEAN DEFAULT NULL,
    last_validated_at TIMESTAMP,
    validation_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Индексы
CREATE INDEX IF NOT EXISTS idx_wb_api_keys_is_valid ON solution.wb_api_keys(is_valid);

-- Комментарии
COMMENT ON TABLE solution.wb_api_keys IS 'Таблица для хранения WB API ключей пользователей. Связь 1:1 - у каждого SELLER только один ключ со всеми правами. user_id является первичным ключом';
COMMENT ON COLUMN solution.wb_api_keys.user_id IS 'ID пользователя (SELLER). Первичный ключ - связь 1:1';
COMMENT ON COLUMN solution.wb_api_keys.api_key IS 'WB API ключ Wildberries со всеми правами доступа';
COMMENT ON COLUMN solution.wb_api_keys.is_valid IS 'Статус валидности ключа (NULL - не проверен, TRUE - валиден, FALSE - невалиден)';
COMMENT ON COLUMN solution.wb_api_keys.last_validated_at IS 'Дата последней проверки ключа';
COMMENT ON COLUMN solution.wb_api_keys.validation_error IS 'Текст ошибки при валидации (если ключ невалиден)';
COMMENT ON COLUMN solution.wb_api_keys.created_at IS 'Дата создания записи';
COMMENT ON COLUMN solution.wb_api_keys.updated_at IS 'Дата последнего обновления записи';

