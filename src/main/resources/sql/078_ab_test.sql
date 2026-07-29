-- А/Б-тест главного фото карточки WB: тесты, варианты, РК, журнал ротаций, снимки статистики.

CREATE TABLE IF NOT EXISTS solution.ab_test (
    id BIGSERIAL PRIMARY KEY,
    cabinet_id BIGINT NOT NULL,
    nm_id BIGINT NOT NULL,
    -- PENDING_START | ENABLED | DISABLED
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_START',
    -- ROTATION_BY_VIEWS | ROTATION_BY_INTERVAL
    rotation_mode VARCHAR(32) NOT NULL,
    rotation_views_threshold INTEGER,
    rotation_interval_minutes INTEGER,
    -- TRUST_US | BY_DURATION
    stop_mode VARCHAR(32) NOT NULL,
    duration_days INTEGER,
    ends_at TIMESTAMP,
    -- KEEP_WINNER | RESTORE_ORIGINAL
    finish_action VARCHAR(32) NOT NULL,
    original_main_photo_url VARCHAR(1000),
    -- JSON-массив URL остальных фото карточки (без главного) на момент старта
    original_gallery_urls_json TEXT,
    active_variant_id BIGINT,
    active_since_views BIGINT NOT NULL DEFAULT 0,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    last_rotated_at TIMESTAMP,
    last_stats_at TIMESTAMP,
    insight_code VARCHAR(32),
    last_wb_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ab_test_cabinet FOREIGN KEY (cabinet_id) REFERENCES solution.cabinets(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ab_test_cabinet_status ON solution.ab_test(cabinet_id, status);
CREATE INDEX IF NOT EXISTS idx_ab_test_nm ON solution.ab_test(cabinet_id, nm_id);
CREATE INDEX IF NOT EXISTS idx_ab_test_enabled ON solution.ab_test(status, last_rotated_at);

COMMENT ON TABLE solution.ab_test IS 'А/Б-тест главного фото карточки WB';
COMMENT ON COLUMN solution.ab_test.status IS 'PENDING_START — ждём WB; ENABLED — активен; DISABLED — завершён';
COMMENT ON COLUMN solution.ab_test.rotation_mode IS 'ROTATION_BY_VIEWS — смена по показам; ROTATION_BY_INTERVAL — по времени';
COMMENT ON COLUMN solution.ab_test.stop_mode IS 'TRUST_US — автостоп по эвристике; BY_DURATION — по сроку';
COMMENT ON COLUMN solution.ab_test.finish_action IS 'KEEP_WINNER — оставить лучший CTR; RESTORE_ORIGINAL — вернуть исходное';
COMMENT ON COLUMN solution.ab_test.insight_code IS 'DATA_LOW | NO_DIFF | HAS_LEADER — статусная строка списка';
COMMENT ON COLUMN solution.ab_test.active_since_views IS 'Накопленные views активного варианта на момент последней ротации';
COMMENT ON COLUMN solution.ab_test.last_wb_error IS 'Текст последней ошибки WB API (старт/ротация/статистика)';

CREATE TABLE IF NOT EXISTS solution.ab_test_campaign (
    id BIGSERIAL PRIMARY KEY,
    ab_test_id BIGINT NOT NULL,
    advert_id BIGINT NOT NULL,
    CONSTRAINT fk_ab_test_campaign_test FOREIGN KEY (ab_test_id) REFERENCES solution.ab_test(id) ON DELETE CASCADE,
    CONSTRAINT uq_ab_test_campaign UNIQUE (ab_test_id, advert_id)
);

COMMENT ON TABLE solution.ab_test_campaign IS 'РК, по которым собирается статистика А/Б-теста';

CREATE TABLE IF NOT EXISTS solution.ab_test_variant (
    id BIGSERIAL PRIMARY KEY,
    ab_test_id BIGINT NOT NULL,
    sort_order INTEGER NOT NULL,
    is_control BOOLEAN NOT NULL DEFAULT FALSE,
    photo_url VARCHAR(1000),
    preview_url VARCHAR(1000),
    stored_file_name VARCHAR(512),
    views BIGINT NOT NULL DEFAULT 0,
    clicks BIGINT NOT NULL DEFAULT 0,
    atbs BIGINT NOT NULL DEFAULT 0,
    orders BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ab_test_variant_test FOREIGN KEY (ab_test_id) REFERENCES solution.ab_test(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ab_test_variant_test ON solution.ab_test_variant(ab_test_id, sort_order);

COMMENT ON TABLE solution.ab_test_variant IS 'Вариант главного фото в А/Б-тесте';
COMMENT ON COLUMN solution.ab_test_variant.is_control IS 'true — исходное главное фото карточки';
COMMENT ON COLUMN solution.ab_test_variant.stored_file_name IS 'Имя файла в app.uploads (для media/file)';

CREATE TABLE IF NOT EXISTS solution.ab_test_rotation_log (
    id BIGSERIAL PRIMARY KEY,
    ab_test_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    switched_at TIMESTAMP NOT NULL,
    reason VARCHAR(64),
    CONSTRAINT fk_ab_test_rotation_test FOREIGN KEY (ab_test_id) REFERENCES solution.ab_test(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ab_test_rotation_test_time ON solution.ab_test_rotation_log(ab_test_id, switched_at);

COMMENT ON TABLE solution.ab_test_rotation_log IS 'Журнал смен активного варианта фото';

CREATE TABLE IF NOT EXISTS solution.ab_test_stats_snapshot (
    id BIGSERIAL PRIMARY KEY,
    ab_test_id BIGINT NOT NULL,
    advert_id BIGINT NOT NULL,
    nm_id BIGINT NOT NULL,
    views INTEGER NOT NULL DEFAULT 0,
    clicks INTEGER NOT NULL DEFAULT 0,
    atbs INTEGER NOT NULL DEFAULT 0,
    orders INTEGER NOT NULL DEFAULT 0,
    captured_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ab_test_snapshot_test FOREIGN KEY (ab_test_id) REFERENCES solution.ab_test(id) ON DELETE CASCADE,
    CONSTRAINT uq_ab_test_snapshot UNIQUE (ab_test_id, advert_id, nm_id)
);

COMMENT ON TABLE solution.ab_test_stats_snapshot IS 'Последний снимок fullstats для расчёта дельт по активному варианту';
