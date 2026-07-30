-- Прогресс загрузки варианта на WB во время пошагового старта А/Б-теста (не теряем работу при defer).

ALTER TABLE solution.ab_test_variant
    ADD COLUMN IF NOT EXISTS wb_uploaded BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN solution.ab_test_variant.wb_uploaded IS
    'true — файл варианта уже отправлен на WB (media/file) в ходе старта; шаг можно не повторять';
