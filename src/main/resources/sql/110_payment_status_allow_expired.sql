-- Разрешаем статус expired для автоотмены просроченных pending-платежей (PaymentScheduler).
-- Старый CHECK допускал только pending/success/failed/refunded — из-за этого джоба падала каждый день в 06:00.

ALTER TABLE solution.payments
    DROP CONSTRAINT IF EXISTS chk_payment_status;

ALTER TABLE solution.payments
    ADD CONSTRAINT chk_payment_status
        CHECK (status IN ('pending', 'success', 'failed', 'refunded', 'expired'));

COMMENT ON COLUMN solution.payments.status IS
    'pending, success, failed, refunded, expired';
