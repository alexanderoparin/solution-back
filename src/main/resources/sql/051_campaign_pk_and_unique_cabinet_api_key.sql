-- PK управления РК только по campaign_id; уникальность API-ключа кабинета.

-- Удалить дубликаты API-ключа (оставить кабинет с максимальным id).
DELETE FROM solution.cabinets c
WHERE c.api_key IS NOT NULL
  AND btrim(c.api_key) <> ''
  AND EXISTS (
    SELECT 1
    FROM solution.cabinets c2
    WHERE c2.api_key = c.api_key
      AND c2.id > c.id
  );

CREATE UNIQUE INDEX IF NOT EXISTS uq_cabinets_api_key
    ON solution.cabinets (api_key)
    WHERE api_key IS NOT NULL AND btrim(api_key) <> '';

COMMENT ON INDEX solution.uq_cabinets_api_key IS 'Один WB API-ключ — один кабинет в системе';

-- Миграция с составного PK (campaign_id, cabinet_id), если 050 уже применялась в старом виде.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'campaign_auto_budget_settings_pkey'
          AND conrelid = 'solution.campaign_auto_budget_settings'::regclass
          AND array_length(conkey, 1) > 1
    ) THEN
        DELETE FROM solution.campaign_auto_budget_settings s
        WHERE NOT EXISTS (
            SELECT 1
            FROM solution.promotion_campaigns p
            WHERE p.advert_id = s.campaign_id AND p.cabinet_id = s.cabinet_id
        );

        DELETE FROM solution.campaign_auto_budget_settings s
        WHERE s.ctid NOT IN (
            SELECT DISTINCT ON (s2.campaign_id) s2.ctid
            FROM solution.campaign_auto_budget_settings s2
            INNER JOIN solution.promotion_campaigns p
                ON p.advert_id = s2.campaign_id AND p.cabinet_id = s2.cabinet_id
            ORDER BY s2.campaign_id, s2.updated_at DESC NULLS LAST
        );

        ALTER TABLE solution.campaign_auto_budget_settings DROP CONSTRAINT campaign_auto_budget_settings_pkey;
        ALTER TABLE solution.campaign_auto_budget_settings ADD PRIMARY KEY (campaign_id);
    END IF;
END $$;

ALTER TABLE solution.campaign_auto_budget_settings
    DROP CONSTRAINT IF EXISTS fk_campaign_auto_budget_campaign;
ALTER TABLE solution.campaign_auto_budget_settings
    ADD CONSTRAINT fk_campaign_auto_budget_campaign
    FOREIGN KEY (campaign_id) REFERENCES solution.promotion_campaigns(advert_id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_campaign_auto_budget_cabinet
    ON solution.campaign_auto_budget_settings(cabinet_id);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'campaign_management_state_pkey'
          AND conrelid = 'solution.campaign_management_state'::regclass
          AND array_length(conkey, 1) > 1
    ) THEN
        DELETE FROM solution.campaign_management_state s
        WHERE NOT EXISTS (
            SELECT 1
            FROM solution.promotion_campaigns p
            WHERE p.advert_id = s.campaign_id AND p.cabinet_id = s.cabinet_id
        );

        DELETE FROM solution.campaign_management_state s
        WHERE s.ctid NOT IN (
            SELECT DISTINCT ON (s2.campaign_id) s2.ctid
            FROM solution.campaign_management_state s2
            INNER JOIN solution.promotion_campaigns p
                ON p.advert_id = s2.campaign_id AND p.cabinet_id = s2.cabinet_id
            ORDER BY s2.campaign_id, s2.updated_at DESC NULLS LAST
        );

        ALTER TABLE solution.campaign_management_state DROP CONSTRAINT campaign_management_state_pkey;
        ALTER TABLE solution.campaign_management_state ADD PRIMARY KEY (campaign_id);
    END IF;
END $$;

ALTER TABLE solution.campaign_management_state
    DROP CONSTRAINT IF EXISTS fk_campaign_management_state_campaign;
ALTER TABLE solution.campaign_management_state
    ADD CONSTRAINT fk_campaign_management_state_campaign
    FOREIGN KEY (campaign_id) REFERENCES solution.promotion_campaigns(advert_id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_campaign_management_state_cabinet
    ON solution.campaign_management_state(cabinet_id);
