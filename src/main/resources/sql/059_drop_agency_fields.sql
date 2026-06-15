-- Фаза C: убрать модель «селлер агентства»; owner_id у SELLER обнуляем до миграции 060.

UPDATE solution.users
SET owner_id = NULL
WHERE role = 'SELLER'
  AND owner_id IS NOT NULL;

ALTER TABLE solution.users
    DROP COLUMN IF EXISTS is_agency_client;
