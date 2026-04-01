CREATE TABLE IF NOT EXISTS solution.wb_api_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    status VARCHAR(40) NOT NULL,
    executor_bean_name VARCHAR(120) NOT NULL,
    cabinet_id BIGINT NOT NULL REFERENCES solution.cabinets(id) ON DELETE CASCADE,
    payload_json TEXT,
    dedup_key VARCHAR(255) NOT NULL,
    attempt_count INTEGER NOT NULL,
    max_attempts INTEGER NOT NULL,
    next_attempt_at TIMESTAMP NOT NULL,
    last_error TEXT,
    priority INTEGER NOT NULL,
    trigger_source VARCHAR(40) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL
);

COMMENT ON TABLE solution.wb_api_events IS 'Очередь событий вызовов WB API. Одно событие соответствует одному HTTP-запросу или шагу диспетчеризации.';
COMMENT ON COLUMN solution.wb_api_events.id IS 'Уникальный идентификатор события';
COMMENT ON COLUMN solution.wb_api_events.event_type IS 'Тип события (например CONTENT_CARDS_LIST_PAGE)';
COMMENT ON COLUMN solution.wb_api_events.status IS 'Текущий статус выполнения события';
COMMENT ON COLUMN solution.wb_api_events.executor_bean_name IS 'Имя Spring-бина исполнителя события';
COMMENT ON COLUMN solution.wb_api_events.cabinet_id IS 'Кабинет, к которому относится событие';
COMMENT ON COLUMN solution.wb_api_events.payload_json IS 'JSON-параметры события для исполнителя';
COMMENT ON COLUMN solution.wb_api_events.dedup_key IS 'Ключ дедупликации для недопущения дубликатов активных событий';
COMMENT ON COLUMN solution.wb_api_events.attempt_count IS 'Количество уже выполненных попыток';
COMMENT ON COLUMN solution.wb_api_events.max_attempts IS 'Максимально допустимое количество попыток';
COMMENT ON COLUMN solution.wb_api_events.next_attempt_at IS 'Когда событие можно выполнять в следующий раз';
COMMENT ON COLUMN solution.wb_api_events.last_error IS 'Текст последней ошибки выполнения';
COMMENT ON COLUMN solution.wb_api_events.priority IS 'Приоритет события (чем больше, тем раньше обработка)';
COMMENT ON COLUMN solution.wb_api_events.trigger_source IS 'Источник запуска события (SCHEDULED, MANUAL_ADMIN и т.д.)';
COMMENT ON COLUMN solution.wb_api_events.created_at IS 'Время создания события';
COMMENT ON COLUMN solution.wb_api_events.started_at IS 'Время начала текущей/последней попытки';
COMMENT ON COLUMN solution.wb_api_events.finished_at IS 'Время финального завершения события';
COMMENT ON COLUMN solution.wb_api_events.updated_at IS 'Время последнего изменения записи события';
