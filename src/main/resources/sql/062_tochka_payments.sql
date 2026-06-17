-- Интеграция с Точка Банк: снимок тарифа на момент оплаты.

ALTER TABLE solution.plans
    DROP COLUMN IF EXISTS product_code;

ALTER TABLE solution.payments
    ADD COLUMN IF NOT EXISTS plan_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS plan_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS period_days INTEGER,
    ADD COLUMN IF NOT EXISTS period_type VARCHAR(20);

COMMENT ON COLUMN solution.payments.plan_code IS 'Код тарифа на момент оплаты, напр. campaign_month';
COMMENT ON COLUMN solution.payments.plan_name IS 'Название тарифа на момент оплаты, напр. Месяц';
COMMENT ON COLUMN solution.payments.period_days IS 'Длительность периода на момент оплаты (snapshot)';
COMMENT ON COLUMN solution.payments.period_type IS 'Тип периода: DAYS или CALENDAR_MONTH';

CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_external_id
    ON solution.payments(external_id)
    WHERE external_id IS NOT NULL;
