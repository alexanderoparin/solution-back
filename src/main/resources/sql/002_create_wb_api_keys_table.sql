-- Создание таблицы wb_api_keys
CREATE TABLE IF NOT EXISTS solution.wb_api_keys (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES solution.users(id) ON DELETE CASCADE,
    api_key VARCHAR(500) NOT NULL,
    is_valid BOOLEAN DEFAULT NULL,
    last_validated_at TIMESTAMP,
    validation_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_wb_api_keys_user_id UNIQUE (user_id)
);

-- Индексы
CREATE INDEX IF NOT EXISTS idx_wb_api_keys_user_id ON solution.wb_api_keys(user_id);
CREATE INDEX IF NOT EXISTS idx_wb_api_keys_is_valid ON solution.wb_api_keys(is_valid);

-- Комментарии
COMMENT ON TABLE solution.wb_api_keys IS 'Таблица для хранения WB API ключей пользователей';
COMMENT ON COLUMN solution.wb_api_keys.id IS 'Уникальный идентификатор записи';
COMMENT ON COLUMN solution.wb_api_keys.user_id IS 'ID пользователя (SELLER)';
COMMENT ON COLUMN solution.wb_api_keys.api_key IS 'WB API ключ (зашифрован)';
COMMENT ON COLUMN solution.wb_api_keys.is_valid IS 'Статус валидности ключа (NULL - не проверен, TRUE - валиден, FALSE - невалиден)';
COMMENT ON COLUMN solution.wb_api_keys.last_validated_at IS 'Дата последней проверки ключа';
COMMENT ON COLUMN solution.wb_api_keys.validation_error IS 'Текст ошибки при валидации (если ключ невалиден)';
COMMENT ON COLUMN solution.wb_api_keys.created_at IS 'Дата создания записи';
COMMENT ON COLUMN solution.wb_api_keys.updated_at IS 'Дата последнего обновления записи';

