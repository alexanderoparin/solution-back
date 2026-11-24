-- Валюта профиля продавца на маркетплейсе (ISO 4217).
-- Для WB: из GET /api/advert/v1/config (или fallback budget/fullstats).
-- По умолчанию RUB — историческое поведение UI.

ALTER TABLE solution.cabinets
    ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'RUB';

COMMENT ON COLUMN solution.cabinets.currency IS
    'Валюта профиля продавца (ISO 4217), например RUB, BYN, KGS, CNY. Денежные метрики WB — в этой валюте.';
