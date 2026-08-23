-- Phase 5 (first slice): credentials и sync-state вне «широкой» таблицы cabinets.
-- Колонки на cabinets остаются источником истины для чтения до cutover;
-- новые таблицы заполняются backfill + dual-write при каждом save кабинета.

CREATE TABLE IF NOT EXISTS solution.cabinet_integrations (
    id                  BIGSERIAL PRIMARY KEY,
    cabinet_id          BIGINT NOT NULL REFERENCES solution.cabinets (id) ON DELETE CASCADE,
    integration_type    VARCHAR(32) NOT NULL,
    -- Секреты: для WB_API / OZON_SELLER — api_key; для OZON_PERFORMANCE — client_secret
    credential_primary  VARCHAR(500),
    -- Доп. идентификатор: OZON_SELLER client_id; OZON_PERFORMANCE client_id; WB token_type в meta
    credential_secondary VARCHAR(128),
    meta_json           TEXT,
    is_valid            BOOLEAN,
    last_validated_at   TIMESTAMP,
    validation_error    TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cabinet_integrations_cabinet_type UNIQUE (cabinet_id, integration_type)
);

COMMENT ON TABLE solution.cabinet_integrations IS 'Учётные данные интеграций кабинета (WB API, Ozon Seller, Ozon Performance). Phase 5 dual-write.';
COMMENT ON COLUMN solution.cabinet_integrations.integration_type IS 'WB_API | OZON_SELLER | OZON_PERFORMANCE';
COMMENT ON COLUMN solution.cabinet_integrations.credential_primary IS 'Основной секрет (api_key или client_secret)';
COMMENT ON COLUMN solution.cabinet_integrations.credential_secondary IS 'Второй идентификатор (client_id и т.п.)';
COMMENT ON COLUMN solution.cabinet_integrations.meta_json IS 'Доп. метаданные (например token_type для WB)';

CREATE INDEX IF NOT EXISTS idx_cabinet_integrations_cabinet
    ON solution.cabinet_integrations (cabinet_id);

CREATE TABLE IF NOT EXISTS solution.cabinet_sync_state (
    cabinet_id                          BIGINT PRIMARY KEY
        REFERENCES solution.cabinets (id) ON DELETE CASCADE,
    last_data_update_at                 TIMESTAMP,
    last_data_update_requested_at       TIMESTAMP,
    last_stocks_update_at               TIMESTAMP,
    last_stocks_update_requested_at     TIMESTAMP,
    last_ozon_campaigns_sync_at         TIMESTAMP,
    created_at                          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                          TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE solution.cabinet_sync_state IS 'Метки синхронизации кабинета. Phase 5 dual-write; до cutover зеркало колонок cabinets.';

-- Backfill WB_API
INSERT INTO solution.cabinet_integrations (
    cabinet_id, integration_type, credential_primary, credential_secondary, meta_json,
    is_valid, last_validated_at, validation_error, created_at, updated_at
)
SELECT
    c.id,
    'WB_API',
    c.api_key,
    NULL,
    CASE WHEN c.token_type IS NOT NULL THEN json_build_object('tokenType', c.token_type)::text ELSE NULL END,
    c.is_valid,
    c.last_validated_at,
    c.validation_error,
    COALESCE(c.created_at, NOW()),
    COALESCE(c.updated_at, NOW())
FROM solution.cabinets c
WHERE c.marketplace_type = 'WB'
  AND NOT EXISTS (
      SELECT 1 FROM solution.cabinet_integrations i
      WHERE i.cabinet_id = c.id AND i.integration_type = 'WB_API'
  );

-- Backfill OZON_SELLER
INSERT INTO solution.cabinet_integrations (
    cabinet_id, integration_type, credential_primary, credential_secondary, meta_json,
    is_valid, last_validated_at, validation_error, created_at, updated_at
)
SELECT
    c.id,
    'OZON_SELLER',
    c.api_key,
    c.ozon_client_id,
    NULL,
    c.is_valid,
    c.last_validated_at,
    c.validation_error,
    COALESCE(c.created_at, NOW()),
    COALESCE(c.updated_at, NOW())
FROM solution.cabinets c
WHERE c.marketplace_type = 'OZON'
  AND NOT EXISTS (
      SELECT 1 FROM solution.cabinet_integrations i
      WHERE i.cabinet_id = c.id AND i.integration_type = 'OZON_SELLER'
  );

-- Backfill OZON_PERFORMANCE (только если задан хотя бы один credential)
INSERT INTO solution.cabinet_integrations (
    cabinet_id, integration_type, credential_primary, credential_secondary, meta_json,
    is_valid, last_validated_at, validation_error, created_at, updated_at
)
SELECT
    c.id,
    'OZON_PERFORMANCE',
    c.ozon_performance_client_secret,
    c.ozon_performance_client_id,
    NULL,
    c.ozon_performance_is_valid,
    c.ozon_performance_last_validated_at,
    c.ozon_performance_validation_error,
    COALESCE(c.created_at, NOW()),
    COALESCE(c.updated_at, NOW())
FROM solution.cabinets c
WHERE c.marketplace_type = 'OZON'
  AND (
      (c.ozon_performance_client_id IS NOT NULL AND btrim(c.ozon_performance_client_id) <> '')
      OR (c.ozon_performance_client_secret IS NOT NULL AND btrim(c.ozon_performance_client_secret) <> '')
  )
  AND NOT EXISTS (
      SELECT 1 FROM solution.cabinet_integrations i
      WHERE i.cabinet_id = c.id AND i.integration_type = 'OZON_PERFORMANCE'
  );

-- Backfill sync state
INSERT INTO solution.cabinet_sync_state (
    cabinet_id,
    last_data_update_at,
    last_data_update_requested_at,
    last_stocks_update_at,
    last_stocks_update_requested_at,
    last_ozon_campaigns_sync_at,
    created_at,
    updated_at
)
SELECT
    c.id,
    c.last_data_update_at,
    c.last_data_update_requested_at,
    c.last_stocks_update_at,
    c.last_stocks_update_requested_at,
    c.last_ozon_campaigns_sync_at,
    COALESCE(c.created_at, NOW()),
    COALESCE(c.updated_at, NOW())
FROM solution.cabinets c
WHERE NOT EXISTS (
    SELECT 1 FROM solution.cabinet_sync_state s WHERE s.cabinet_id = c.id
);
