-- Признак: тест не дошёл до ENABLED (ошибка старта / отмена до запуска) — можно перезапустить после смены токена.

ALTER TABLE solution.ab_test
    ADD COLUMN IF NOT EXISTS failed_at_start BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN solution.ab_test.failed_at_start IS
    'true — старт не завершился (PENDING_START → DISABLED); можно перезапустить без создания нового теста';

-- Уже упавшие на 401 / токене — разрешаем перезапуск.
UPDATE solution.ab_test
SET failed_at_start = TRUE
WHERE status = 'DISABLED'
  AND last_wb_error IS NOT NULL
  AND (
      last_wb_error ILIKE '%401%'
      OR last_wb_error ILIKE '%unauthorized%'
      OR last_wb_error ILIKE '%токен%'
  );
