-- Phase 5.2 cutover: credentials и sync-state только в cabinet_integrations / cabinet_sync_state.
-- Требует применённого 110 (backfill). Колонки на cabinets удаляются.

-- Уникальность API-ключа (WB / Ozon Seller) переносится на integrations.
DROP INDEX IF EXISTS solution.uq_cabinets_api_key;

-- Дубликаты credential_primary (оставить строку с max id).
DELETE FROM solution.cabinet_integrations i
WHERE i.credential_primary IS NOT NULL
  AND btrim(i.credential_primary) <> ''
  AND i.integration_type IN ('WB_API', 'OZON_SELLER')
  AND EXISTS (
      SELECT 1
      FROM solution.cabinet_integrations i2
      WHERE i2.credential_primary = i.credential_primary
        AND i2.integration_type IN ('WB_API', 'OZON_SELLER')
        AND i2.id > i.id
  );

CREATE UNIQUE INDEX IF NOT EXISTS uq_cabinet_integrations_api_key
    ON solution.cabinet_integrations (credential_primary)
    WHERE integration_type IN ('WB_API', 'OZON_SELLER')
      AND credential_primary IS NOT NULL
      AND btrim(credential_primary) <> '';

COMMENT ON INDEX solution.uq_cabinet_integrations_api_key IS
    'Один Seller/WB API-ключ — один кабинет (Phase 5.2 cutover)';

ALTER TABLE solution.cabinets DROP COLUMN IF EXISTS api_key;
ALTER TABLE solution.cabinets DROP COLUMN IF EXISTS ozon_client_id;
ALTER TABLE solution.cabinets DROP COLUMN IF EXISTS ozon_performance_client_id;
ALTER TABLE solution.cabinets DROP COLUMN IF EXISTS ozon_performance_client_secret;
ALTER TABLE solution.cabinets DROP COLUMN IF EXISTS ozon_performance_is_valid;
ALTER TABLE solution.cabinets DROP COLUMN IF EXISTS ozon_performance_last_validated_at;
ALTER TABLE solution.cabinets DROP COLUMN IF EXISTS ozon_performance_validation_error;
ALTER TABLE solution.cabinets DROP COLUMN IF EXISTS last_ozon_campaigns_sync_at;
ALTER TABLE solution.cabinets DROP COLUMN IF EXISTS token_type;
ALTER TABLE solution.cabinets DROP COLUMN IF EXISTS is_valid;
ALTER TABLE solution.cabinets DROP COLUMN IF EXISTS last_validated_at;
ALTER TABLE solution.cabinets DROP COLUMN IF EXISTS validation_error;
ALTER TABLE solution.cabinets DROP COLUMN IF EXISTS last_data_update_at;
ALTER TABLE solution.cabinets DROP COLUMN IF EXISTS last_data_update_requested_at;
ALTER TABLE solution.cabinets DROP COLUMN IF EXISTS last_stocks_update_requested_at;
ALTER TABLE solution.cabinets DROP COLUMN IF EXISTS last_stocks_update_at;

COMMENT ON TABLE solution.cabinets IS
    'Кабинет продавца: user_id, marketplace_type, name, audit. Credentials — cabinet_integrations.';
