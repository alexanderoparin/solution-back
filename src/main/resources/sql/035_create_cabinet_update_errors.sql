CREATE TABLE IF NOT EXISTS solution.cabinet_update_errors (
    id BIGSERIAL PRIMARY KEY,
    cabinet_id BIGINT NOT NULL REFERENCES solution.cabinets(id) ON DELETE CASCADE,
    scope VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    error_message TEXT NOT NULL
);

COMMENT ON TABLE solution.cabinet_update_errors IS 'Журнал ошибок обновления данных по кабинету. Содержит время и текст ошибки для этапов основного обновления и обновления остатков.';
COMMENT ON COLUMN solution.cabinet_update_errors.id IS 'Уникальный идентификатор записи';
COMMENT ON COLUMN solution.cabinet_update_errors.cabinet_id IS 'Кабинет, для которого произошла ошибка обновления';
COMMENT ON COLUMN solution.cabinet_update_errors.scope IS 'Этап обновления, на котором произошла ошибка (MAIN или STOCKS)';
COMMENT ON COLUMN solution.cabinet_update_errors.occurred_at IS 'Дата и время возникновения ошибки';
COMMENT ON COLUMN solution.cabinet_update_errors.error_message IS 'Текст ошибки обновления';
