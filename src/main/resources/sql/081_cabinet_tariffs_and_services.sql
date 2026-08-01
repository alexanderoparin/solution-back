-- Тарифы и услуги на уровне кабинета: PRO, пакеты А/Б, квоты, cabinet_id на подписках/платежах.

-- =============================================================================
-- plans.kind + credit_amount
-- =============================================================================
ALTER TABLE solution.plans
    ADD COLUMN IF NOT EXISTS kind VARCHAR(20),
    ADD COLUMN IF NOT EXISTS credit_amount INTEGER;

COMMENT ON COLUMN solution.plans.kind IS 'MAIN | CAMPAIGN | AB_PACK';
COMMENT ON COLUMN solution.plans.credit_amount IS 'Число кредитов А/Б для пакетов (AB_PACK); иначе NULL';

UPDATE solution.plans
SET kind = 'CAMPAIGN',
    updated_at = now()
WHERE code LIKE 'campaign_%'
  AND (kind IS NULL OR kind = '');

UPDATE solution.plans
SET kind = 'MAIN',
    updated_at = now()
WHERE code = 'analytics_free'
  AND (kind IS NULL OR kind = '');

UPDATE solution.plans
SET price_rub = 3800,
    updated_at = now()
WHERE code = 'campaign_month'
  AND price_rub IS DISTINCT FROM 3800;

-- PRO (основной платный тариф)
INSERT INTO solution.plans (
    name, description, price_rub, period_days, sort_order, is_active, code, period_type, kind, credit_amount
)
SELECT
    'PRO',
    'Полный доступ ко всем разделам без ограничений.',
    9990::numeric,
    30,
    10,
    true,
    'pro_month',
    'CALENDAR_MONTH',
    'MAIN',
    NULL
WHERE NOT EXISTS (SELECT 1 FROM solution.plans p WHERE p.code = 'pro_month');

-- Пакеты А/Б тестов
INSERT INTO solution.plans (
    name, description, price_rub, period_days, sort_order, is_active, code, period_type, kind, credit_amount
)
SELECT v.name, v.description, v.price_rub, v.period_days, v.sort_order, v.is_active, v.code, v.period_type, v.kind, v.credit_amount
FROM (VALUES
    (
        '1 тест',
        'Для одного А/Б теста',
        500::numeric,
        0,
        20,
        true,
        'ab_pack_1',
        'DAYS',
        'AB_PACK',
        1
    ),
    (
        '5 тестов',
        'Для регулярного А/Б тестирования и поиска лучшего CTR',
        1990::numeric,
        0,
        21,
        true,
        'ab_pack_5',
        'DAYS',
        'AB_PACK',
        5
    ),
    (
        '10 тестов',
        'Самый выгодный пакет для постоянной работы с АБ тестами и роста конверсии',
        2990::numeric,
        0,
        22,
        true,
        'ab_pack_10',
        'DAYS',
        'AB_PACK',
        10
    )
) AS v(name, description, price_rub, period_days, sort_order, is_active, code, period_type, kind, credit_amount)
WHERE NOT EXISTS (SELECT 1 FROM solution.plans p WHERE p.code = v.code);

UPDATE solution.plans
SET description = 'Разделы Товары, Сводная и Рекламные кампании. Дополнительные услуги подключаются отдельно.',
    name = 'Бесплатный доступ',
    updated_at = now()
WHERE code = 'analytics_free';

-- =============================================================================
-- subscriptions.cabinet_id / payments.cabinet_id
-- =============================================================================
ALTER TABLE solution.subscriptions
    ADD COLUMN IF NOT EXISTS cabinet_id BIGINT;

ALTER TABLE solution.payments
    ADD COLUMN IF NOT EXISTS cabinet_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_subscriptions_cabinet'
    ) THEN
        ALTER TABLE solution.subscriptions
            ADD CONSTRAINT fk_subscriptions_cabinet
            FOREIGN KEY (cabinet_id) REFERENCES solution.cabinets (id) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_payments_cabinet'
    ) THEN
        ALTER TABLE solution.payments
            ADD CONSTRAINT fk_payments_cabinet
            FOREIGN KEY (cabinet_id) REFERENCES solution.cabinets (id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_subscriptions_cabinet_id ON solution.subscriptions (cabinet_id);
CREATE INDEX IF NOT EXISTS idx_payments_cabinet_id ON solution.payments (cabinet_id);

COMMENT ON COLUMN solution.subscriptions.cabinet_id IS 'Кабинет, к которому привязана подписка/услуга';
COMMENT ON COLUMN solution.payments.cabinet_id IS 'Кабинет, за который оплата';

-- Перенос активных campaign_* с пользователя на все его кабинеты
INSERT INTO solution.subscriptions (
    user_id, plan_id, status, started_at, expires_at, auto_renew, created_at, updated_at, cabinet_id
)
SELECT
    s.user_id,
    s.plan_id,
    s.status,
    s.started_at,
    s.expires_at,
    COALESCE(s.auto_renew, false),
    now(),
    now(),
    c.id
FROM solution.subscriptions s
JOIN solution.plans p ON p.id = s.plan_id
JOIN solution.cabinets c ON c.user_id = s.user_id
WHERE s.cabinet_id IS NULL
  AND p.code LIKE 'campaign_%'
  AND s.status IN ('active', 'trial')
  AND (s.expires_at IS NULL OR s.expires_at > now())
  AND NOT EXISTS (
      SELECT 1
      FROM solution.subscriptions s2
      JOIN solution.plans p2 ON p2.id = s2.plan_id
      WHERE s2.cabinet_id = c.id
        AND p2.code LIKE 'campaign_%'
        AND s2.status IN ('active', 'trial')
        AND (s2.expires_at IS NULL OR s2.expires_at > now())
  );

-- FREE (analytics_free) на каждый кабинет без основной подписки
INSERT INTO solution.subscriptions (
    user_id, plan_id, status, started_at, expires_at, auto_renew, created_at, updated_at, cabinet_id
)
SELECT
    c.user_id,
    p.id,
    'active',
    now(),
    NULL,
    true,
    now(),
    now(),
    c.id
FROM solution.cabinets c
CROSS JOIN solution.plans p
WHERE p.code = 'analytics_free'
  AND NOT EXISTS (
      SELECT 1
      FROM solution.subscriptions s
      JOIN solution.plans p2 ON p2.id = s.plan_id
      WHERE s.cabinet_id = c.id
        AND p2.kind = 'MAIN'
        AND s.status IN ('active', 'trial')
        AND (s.expires_at IS NULL OR s.expires_at > now())
  );

-- Старые user-scoped строки без кабинета больше не используем для entitlement
UPDATE solution.subscriptions
SET status = 'cancelled',
    updated_at = now()
WHERE cabinet_id IS NULL
  AND status IN ('active', 'trial');

-- =============================================================================
-- Квота А/Б тестов на кабинет
-- =============================================================================
CREATE TABLE IF NOT EXISTS solution.cabinet_ab_test_quota (
    cabinet_id BIGINT PRIMARY KEY REFERENCES solution.cabinets (id) ON DELETE CASCADE,
    remaining INTEGER NOT NULL DEFAULT 3,
    used_starts INTEGER NOT NULL DEFAULT 0,
    included_free INTEGER NOT NULL DEFAULT 3,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

COMMENT ON TABLE solution.cabinet_ab_test_quota IS 'Квота запусков А/Б тестов кабинета (без срока годности)';
COMMENT ON COLUMN solution.cabinet_ab_test_quota.remaining IS 'Доступно запусков';
COMMENT ON COLUMN solution.cabinet_ab_test_quota.used_starts IS 'Успешных стартов (списаний)';
COMMENT ON COLUMN solution.cabinet_ab_test_quota.included_free IS 'Стартовый бесплатный пакет';

INSERT INTO solution.cabinet_ab_test_quota (cabinet_id, remaining, used_starts, included_free)
SELECT c.id, 3, 0, 3
FROM solution.cabinets c
WHERE NOT EXISTS (
    SELECT 1 FROM solution.cabinet_ab_test_quota q WHERE q.cabinet_id = c.id
);
