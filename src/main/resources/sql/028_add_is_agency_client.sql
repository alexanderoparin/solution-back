-- Явный флаг «клиент агентства» для селлера (создан менеджером/админом, привязан к owner).
ALTER TABLE solution.users
  ADD COLUMN IF NOT EXISTS is_agency_client BOOLEAN NOT NULL DEFAULT false;

-- Синхронизация с owner_id: селлеры с владельцем — клиенты агентства
UPDATE solution.users
SET is_agency_client = true
WHERE owner_id IS NOT NULL AND role = 'SELLER';

COMMENT ON COLUMN solution.users.is_agency_client IS 'Селлер является клиентом агентства (создан менеджером/админом, привязан к owner)';
