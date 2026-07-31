-- Пауза варианта А/Б-теста: исключается из ротации, статистика сохраняется.

ALTER TABLE solution.ab_test_variant
    ADD COLUMN IF NOT EXISTS paused BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN solution.ab_test_variant.paused IS
    'true — вариант на паузе: не участвует в ротации (можно снять проигрывающий)';
