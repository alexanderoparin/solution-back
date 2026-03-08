-- Статус доступа к категориям WB API по кабинету (один ключ — один набор категорий).
-- Обновляется после каждого блока обновлений: успех — success=true, при 401 — success=false.
CREATE TABLE IF NOT EXISTS solution.cabinet_scope_status (
    id BIGSERIAL PRIMARY KEY,
    cabinet_id BIGINT NOT NULL REFERENCES solution.cabinets(id) ON DELETE CASCADE,
    category VARCHAR(50) NOT NULL,
    last_checked_at TIMESTAMP NOT NULL,
    success BOOLEAN NOT NULL,
    error_message TEXT,
    CONSTRAINT uq_cabinet_scope_cabinet_category UNIQUE (cabinet_id, category)
);

COMMENT ON TABLE solution.cabinet_scope_status IS 'Результат последней проверки доступа к категории WB API по кабинету. Обновляется после каждого блока обновлений (успех — true, при 401 — false).';
COMMENT ON COLUMN solution.cabinet_scope_status.id IS 'Уникальный идентификатор записи';
COMMENT ON COLUMN solution.cabinet_scope_status.cabinet_id IS 'Кабинет (ключ привязан к кабинету)';
COMMENT ON COLUMN solution.cabinet_scope_status.category IS 'Категория WB API (CONTENT, ANALYTICS, PRICES_AND_DISCOUNTS, PROMOTION и т.д.)';
COMMENT ON COLUMN solution.cabinet_scope_status.last_checked_at IS 'Дата и время последней проверки (завершения блока обновлений по этой категории)';
COMMENT ON COLUMN solution.cabinet_scope_status.success IS 'Успешно ли прошёл последний блок обновлений по категории (true — успех, false — 401 или ошибка доступа)';
COMMENT ON COLUMN solution.cabinet_scope_status.error_message IS 'Текст ошибки при неуспехе (например сообщение от WB при 401), для отображения в профиле';