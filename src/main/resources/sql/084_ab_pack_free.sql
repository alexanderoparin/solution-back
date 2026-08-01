-- Бесплатный пакет А/Б тестов (карточка FREE в каталоге).
-- credit_amount можно менять в админке — применяется при активации для ещё не активированных кабинетов.

INSERT INTO solution.plans (
    name, description, price_rub, period_days, sort_order, is_active, code, period_type, kind, credit_amount
)
SELECT
    'FREE',
    'Бесплатные А/Б тесты для знакомства с возможностями сервиса',
    0::numeric,
    0,
    19,
    true,
    'ab_pack_free',
    'DAYS',
    'AB_PACK',
    3
WHERE NOT EXISTS (SELECT 1 FROM solution.plans p WHERE p.code = 'ab_pack_free');

COMMENT ON COLUMN solution.plans.credit_amount IS
    'Число кредитов А/Б для пакетов AB_PACK (в т.ч. ab_pack_free)';
