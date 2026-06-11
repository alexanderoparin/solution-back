-- Тарифы «Управление РК»: продуктовый код, тип периода, новые планы.

ALTER TABLE solution.plans
    ADD COLUMN IF NOT EXISTS code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS product_code VARCHAR(50) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN IF NOT EXISTS period_type VARCHAR(20) NOT NULL DEFAULT 'DAYS';

COMMENT ON COLUMN solution.plans.code IS 'Стабильный код плана (campaign_free, campaign_week, …)';
COMMENT ON COLUMN solution.plans.product_code IS 'Продукт: CAMPAIGN_MANAGE, LEGACY и т.д.';
COMMENT ON COLUMN solution.plans.period_type IS 'DAYS — period_days; CALENDAR_MONTH — +1 календарный месяц';

CREATE UNIQUE INDEX IF NOT EXISTS idx_plans_code ON solution.plans (code) WHERE code IS NOT NULL;

UPDATE solution.plans
SET is_active = false,
    product_code = 'LEGACY',
    updated_at = now()
WHERE code IS NULL;

INSERT INTO solution.plans (
    name, description, price_rub, period_days, sort_order, is_active, code, product_code, period_type
)
SELECT v.name, v.description, v.price_rub, v.period_days, v.sort_order, v.is_active, v.code, v.product_code, v.period_type
FROM (VALUES
    (
        'Free',
        'Тем, кто хочет оценить эффективность сервиса на реальных данных до принятия решения о подключении.',
        0::numeric,
        3,
        1,
        true,
        'campaign_free',
        'CAMPAIGN_MANAGE',
        'DAYS'
    ),
    (
        'Неделя',
        'Для тестирования сервиса на собственных РК без долгосрочных обязательств.',
        1000::numeric,
        7,
        2,
        true,
        'campaign_week',
        'CAMPAIGN_MANAGE',
        'DAYS'
    ),
    (
        'Месяц',
        'Для регулярной работы с рекламой и аналитикой.',
        4200::numeric,
        30,
        3,
        true,
        'campaign_month',
        'CAMPAIGN_MANAGE',
        'CALENDAR_MONTH'
    )
) AS v(name, description, price_rub, period_days, sort_order, is_active, code, product_code, period_type)
WHERE NOT EXISTS (SELECT 1 FROM solution.plans p WHERE p.code = v.code);
