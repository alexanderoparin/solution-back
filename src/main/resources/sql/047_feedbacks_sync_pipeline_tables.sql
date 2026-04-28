CREATE TABLE IF NOT EXISTS solution.feedbacks_sync_runs (
    id BIGSERIAL PRIMARY KEY,
    cabinet_id BIGINT NOT NULL REFERENCES solution.cabinets(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    trigger_source VARCHAR(40) NOT NULL,
    token_type_snapshot VARCHAR(20) NOT NULL,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP
);

COMMENT ON TABLE solution.feedbacks_sync_runs IS
    'Запуски пошаговой синхронизации отзывов WB по кабинетам.';
COMMENT ON COLUMN solution.feedbacks_sync_runs.id IS
    'Уникальный идентификатор запуска (run).';
COMMENT ON COLUMN solution.feedbacks_sync_runs.cabinet_id IS
    'Кабинет, для которого выполняется синхронизация отзывов.';
COMMENT ON COLUMN solution.feedbacks_sync_runs.status IS
    'Текущий статус запуска: RUNNING, COMPLETED, FAILED.';
COMMENT ON COLUMN solution.feedbacks_sync_runs.trigger_source IS
    'Источник запуска событий (например SCHEDULED, ADMIN_BULK_FEEDBACKS).';
COMMENT ON COLUMN solution.feedbacks_sync_runs.token_type_snapshot IS
    'Снимок типа токена кабинета на момент старта запуска (BASIC/PERSONAL).';
COMMENT ON COLUMN solution.feedbacks_sync_runs.last_error IS
    'Текст последней ошибки запуска (для диагностики при FAILED).';
COMMENT ON COLUMN solution.feedbacks_sync_runs.created_at IS
    'Время создания запуска.';
COMMENT ON COLUMN solution.feedbacks_sync_runs.updated_at IS
    'Время последнего обновления запуска.';
COMMENT ON COLUMN solution.feedbacks_sync_runs.finished_at IS
    'Время завершения запуска (при COMPLETED/FAILED).';

CREATE INDEX IF NOT EXISTS idx_feedbacks_sync_runs_cabinet_status
    ON solution.feedbacks_sync_runs (cabinet_id, status);

CREATE TABLE IF NOT EXISTS solution.feedbacks_sync_accumulator (
    run_id BIGINT NOT NULL REFERENCES solution.feedbacks_sync_runs(id) ON DELETE CASCADE,
    nm_id BIGINT NOT NULL,
    valuation_sum BIGINT NOT NULL,
    reviews_count BIGINT NOT NULL,
    PRIMARY KEY (run_id, nm_id)
);

COMMENT ON TABLE solution.feedbacks_sync_accumulator IS
    'Промежуточные агрегаты отзывов по nmId в рамках одного запуска.';
COMMENT ON COLUMN solution.feedbacks_sync_accumulator.run_id IS
    'Ссылка на запуск синхронизации (feedbacks_sync_runs.id).';
COMMENT ON COLUMN solution.feedbacks_sync_accumulator.nm_id IS
    'nmId карточки товара WB.';
COMMENT ON COLUMN solution.feedbacks_sync_accumulator.valuation_sum IS
    'Сумма оценок отзывов по nmId (для вычисления среднего рейтинга).';
COMMENT ON COLUMN solution.feedbacks_sync_accumulator.reviews_count IS
    'Количество отзывов по nmId.';

CREATE INDEX IF NOT EXISTS idx_feedbacks_sync_accumulator_run
    ON solution.feedbacks_sync_accumulator (run_id);

CREATE TABLE IF NOT EXISTS solution.feedbacks_sync_page_checkpoint (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES solution.feedbacks_sync_runs(id) ON DELETE CASCADE,
    is_answered BOOLEAN NOT NULL,
    skip_value INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_feedbacks_sync_page UNIQUE (run_id, is_answered, skip_value)
);

COMMENT ON TABLE solution.feedbacks_sync_page_checkpoint IS
    'Идемпотентные чекпоинты обработанных страниц отзывов (run + phase + skip).';
COMMENT ON COLUMN solution.feedbacks_sync_page_checkpoint.id IS
    'Уникальный идентификатор чекпоинта.';
COMMENT ON COLUMN solution.feedbacks_sync_page_checkpoint.run_id IS
    'Ссылка на запуск синхронизации (feedbacks_sync_runs.id).';
COMMENT ON COLUMN solution.feedbacks_sync_page_checkpoint.is_answered IS
    'Фаза выгрузки: true — обработанные отзывы, false — необработанные.';
COMMENT ON COLUMN solution.feedbacks_sync_page_checkpoint.skip_value IS
    'Смещение страницы (skip), уже применённой в агрегатор.';
COMMENT ON COLUMN solution.feedbacks_sync_page_checkpoint.created_at IS
    'Время фиксации чекпоинта.';

CREATE INDEX IF NOT EXISTS idx_feedbacks_sync_page_checkpoint_run
    ON solution.feedbacks_sync_page_checkpoint (run_id);
