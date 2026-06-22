-- Клиент агентства: Управление РК и расписание без подписки campaign_*.
ALTER TABLE solution.users
    ADD COLUMN IF NOT EXISTS agency_managed BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN solution.users.agency_managed IS
    'Клиент агентства: Управление РК и расписание без подписки campaign_* (только для role=SELLER)';
