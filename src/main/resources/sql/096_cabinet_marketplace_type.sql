-- Тип маркетплейса кабинета (WB | OZON). Существующие кабинеты — WB.
-- Поле задаётся при создании и не меняется продуктом.

ALTER TABLE solution.cabinets
    ADD COLUMN IF NOT EXISTS marketplace_type VARCHAR(16) NOT NULL DEFAULT 'WB';

COMMENT ON COLUMN solution.cabinets.marketplace_type IS 'Маркетплейс кабинета: WB или OZON (immutable после создания)';

-- Явно проставляем WB на случай, если DEFAULT не применился к старым строкам в какой-то среде
UPDATE solution.cabinets
SET marketplace_type = 'WB'
WHERE marketplace_type IS NULL OR marketplace_type = '';
