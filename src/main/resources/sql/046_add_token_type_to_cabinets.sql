-- Добавляет тип токена WB API для кабинетов.
-- Все существующие токены переводятся в тип BASIC (базовый).

ALTER TABLE solution.cabinets
    ADD COLUMN IF NOT EXISTS token_type VARCHAR(32);

UPDATE solution.cabinets
SET token_type = 'BASIC'
WHERE token_type IS NULL;

ALTER TABLE solution.cabinets
    ALTER COLUMN token_type SET NOT NULL;

ALTER TABLE solution.cabinets
    ALTER COLUMN token_type SET DEFAULT 'BASIC';
