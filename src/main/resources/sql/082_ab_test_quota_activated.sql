-- Явное подключение услуги А/Б: квота есть, но счётчик и создание — после activate / покупки пакета.

ALTER TABLE solution.cabinet_ab_test_quota
    ADD COLUMN IF NOT EXISTS activated BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN solution.cabinet_ab_test_quota.activated IS
    'Услуга А/Б подключена: бесплатные 3 активированы или куплен пакет';

-- Уже пользовались квотой — считаем подключёнными
UPDATE solution.cabinet_ab_test_quota
SET activated = true,
    updated_at = now()
WHERE used_starts > 0
   OR remaining < included_free;
