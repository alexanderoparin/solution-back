-- Идентификатор аккаунта маркетплейса на интеграции.
-- Для WB_API: claim sid из JWT API-ключа (совпадает с seller-info.sid).
-- Нужен для детекта «тот же магазин WB уже привязан к другому кабинету» при перевыпуске токена.

ALTER TABLE solution.cabinet_integrations
    ADD COLUMN IF NOT EXISTS marketplace_account_id VARCHAR(64);

COMMENT ON COLUMN solution.cabinet_integrations.marketplace_account_id IS
    'Внешний id аккаунта МП: для WB — JWT sid; для поиска дублей при привязке токена';

-- Бэкафил sid из payload JWT (второй сегмент credential_primary).
-- base64url → base64, дополнение padding, decode → json->>'sid'
UPDATE solution.cabinet_integrations AS ci
SET marketplace_account_id = sub.sid
FROM (
    SELECT
        id,
        NULLIF(BTRIM((
            CONVERT_FROM(
                DECODE(
                    RPAD(
                        TRANSLATE(SPLIT_PART(credential_primary, '.', 2), '-_', '+/'),
                        ((LENGTH(TRANSLATE(SPLIT_PART(credential_primary, '.', 2), '-_', '+/')) + 3) / 4) * 4,
                        '='
                    ),
                    'base64'
                ),
                'UTF8'
            )::jsonb ->> 'sid'
        )), '') AS sid
    FROM solution.cabinet_integrations
    WHERE integration_type = 'WB_API'
      AND credential_primary IS NOT NULL
      AND credential_primary LIKE '%.%.%'
      AND marketplace_account_id IS NULL
) AS sub
WHERE ci.id = sub.id
  AND sub.sid IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_cabinet_integrations_mp_account
    ON solution.cabinet_integrations (integration_type, marketplace_account_id)
    WHERE marketplace_account_id IS NOT NULL;
