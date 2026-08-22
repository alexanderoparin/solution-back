-- Очередь событий Ozon Seller API (аналог wb_api_events).

CREATE TABLE IF NOT EXISTS solution.ozon_api_events (
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

COMMENT ON TABLE solution.ozon_api_events IS 'Очередь событий вызовов Ozon Seller API.';
COMMENT ON COLUMN solution.ozon_api_events.event_type IS 'Тип события (например PRODUCT_LIST_PAGE)';
COMMENT ON COLUMN solution.ozon_api_events.cabinet_id IS 'Ozon-кабинет, к которому относится событие';

CREATE INDEX IF NOT EXISTS idx_ozon_api_events_cabinet_status
    ON solution.ozon_api_events (cabinet_id, status);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ozon_api_events_dedup_active
    ON solution.ozon_api_events (dedup_key)
    WHERE status IN ('CREATED', 'RUNNING', 'FAILED_RETRYABLE', 'DEFERRED_RATE_LIMIT');
