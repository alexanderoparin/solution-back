-- Временная блокировка start/pause РК при read-only токене WB (категория PROMOTION в cabinet_scope_status).

ALTER TABLE solution.cabinet_scope_status
    ADD COLUMN IF NOT EXISTS write_blocked_until TIMESTAMP;

COMMENT ON COLUMN solution.cabinet_scope_status.write_blocked_until IS
    'До этого времени запрещены операции записи по категории (например start/pause РК при read-only токене). NULL — ограничения нет.';
