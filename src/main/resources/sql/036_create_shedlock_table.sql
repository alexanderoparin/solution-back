CREATE TABLE IF NOT EXISTS solution.shedlock (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);

COMMENT ON TABLE solution.shedlock IS 'Служебная таблица ShedLock для распределенной блокировки scheduler задач.';
COMMENT ON COLUMN solution.shedlock.name IS 'Уникальное имя блокировки';
COMMENT ON COLUMN solution.shedlock.lock_until IS 'Момент, до которого блокировка считается занятой';
COMMENT ON COLUMN solution.shedlock.locked_at IS 'Момент установки блокировки';
COMMENT ON COLUMN solution.shedlock.locked_by IS 'Идентификатор инстанса, установившего блокировку';
