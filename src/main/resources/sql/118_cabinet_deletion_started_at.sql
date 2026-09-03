-- Фоновое удаление кабинета: сразу скрываем его из списков, тяжёлые DELETE идут асинхронно.

ALTER TABLE solution.cabinets
    ADD COLUMN IF NOT EXISTS deletion_started_at TIMESTAMP NULL;

COMMENT ON COLUMN solution.cabinets.deletion_started_at IS
    'Момент постановки кабинета в очередь на удаление; NULL — кабинет активен';
