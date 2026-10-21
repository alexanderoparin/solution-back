-- Удаление устаревшего payments.cabinet_id.
-- Источник истины по кабинетам платежа: solution.payment_cabinets.

ALTER TABLE solution.payments
    DROP CONSTRAINT IF EXISTS fk_payments_cabinet;

DROP INDEX IF EXISTS solution.idx_payments_cabinet_id;

ALTER TABLE solution.payments
    DROP COLUMN IF EXISTS cabinet_id;
