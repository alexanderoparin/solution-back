-- Мульти-кабинетные тарифы PRO/РК (14 дней / месяц), A/B-квота на пользователя.
-- Trial и старые однокабинетные планы деактивируются. Единственный pro_month → pro_1_month.

-- =============================================================================
-- plans.min_cabinets
-- =============================================================================
ALTER TABLE solution.plans
    ADD COLUMN IF NOT EXISTS min_cabinets INTEGER;

COMMENT ON COLUMN solution.plans.min_cabinets IS 'Минимум кабинетов в пакете (тир 1 / 2–5 / 6–10); NULL для AB_PACK и legacy';

-- =============================================================================
-- payment_cabinets + subscriptions.payment_id
-- =============================================================================
CREATE TABLE IF NOT EXISTS solution.payment_cabinets (
    payment_id BIGINT NOT NULL REFERENCES solution.payments (id) ON DELETE CASCADE,
    cabinet_id BIGINT NOT NULL REFERENCES solution.cabinets (id) ON DELETE CASCADE,
    PRIMARY KEY (payment_id, cabinet_id)
);

COMMENT ON TABLE solution.payment_cabinets IS 'Кабинеты, включённые в одну оплату (корзина)';
COMMENT ON COLUMN solution.payment_cabinets.payment_id IS 'Платёж';
COMMENT ON COLUMN solution.payment_cabinets.cabinet_id IS 'Кабинет из покупки';

CREATE INDEX IF NOT EXISTS idx_payment_cabinets_cabinet_id ON solution.payment_cabinets (cabinet_id);

ALTER TABLE solution.subscriptions
    ADD COLUMN IF NOT EXISTS payment_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_subscriptions_payment'
    ) THEN
        ALTER TABLE solution.subscriptions
            ADD CONSTRAINT fk_subscriptions_payment
            FOREIGN KEY (payment_id) REFERENCES solution.payments (id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_subscriptions_payment_id ON solution.subscriptions (payment_id);

COMMENT ON COLUMN solution.subscriptions.payment_id IS 'Платёж, из которого создана/продлена подписка';

-- =============================================================================
-- user_ab_test_quota (квота А/Б на пользователя)
-- =============================================================================
CREATE TABLE IF NOT EXISTS solution.user_ab_test_quota (
    user_id      BIGINT PRIMARY KEY REFERENCES solution.users (id) ON DELETE CASCADE,
    remaining    INTEGER NOT NULL DEFAULT 0,
    used_starts  INTEGER NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE solution.user_ab_test_quota IS 'Квота запусков А/Б тестов на пользователя (пакеты без срока)';
COMMENT ON COLUMN solution.user_ab_test_quota.user_id IS 'Владелец квоты';
COMMENT ON COLUMN solution.user_ab_test_quota.remaining IS 'Оставшиеся запуски';
COMMENT ON COLUMN solution.user_ab_test_quota.used_starts IS 'Всего списанных запусков';

-- Перенос remaining с кабинетов на владельца (сумма remaining; used_starts тоже суммируем)
INSERT INTO solution.user_ab_test_quota (user_id, remaining, used_starts, created_at, updated_at)
SELECT
    c.user_id,
    COALESCE(SUM(CASE WHEN q.remaining > 0 THEN q.remaining ELSE 0 END), 0)::integer,
    COALESCE(SUM(COALESCE(q.used_starts, 0)), 0)::integer,
    now(),
    now()
FROM solution.wb_cabinet_ab_test_quota q
JOIN solution.cabinets c ON c.id = q.cabinet_id
WHERE c.user_id IS NOT NULL
GROUP BY c.user_id
ON CONFLICT (user_id) DO UPDATE
SET remaining = GREATEST(solution.user_ab_test_quota.remaining, EXCLUDED.remaining),
    used_starts = GREATEST(solution.user_ab_test_quota.used_starts, EXCLUDED.used_starts),
    updated_at = now();

-- =============================================================================
-- Seed новых планов PRO / CAMPAIGN
-- =============================================================================
INSERT INTO solution.plans (
    name, description, price_rub, period_days, sort_order, is_active,
    code, period_type, kind, credit_amount, min_cabinets, max_cabinets
)
SELECT v.name, v.description, v.price_rub, v.period_days, v.sort_order, v.is_active,
       v.code, v.period_type, v.kind, v.credit_amount, v.min_cabinets, v.max_cabinets
FROM (VALUES
    -- PRO: 1 кабинет
    ('Полный доступ · 1 кабинет · 14 дней',
     'Полный доступ ко всем функциям на 1 кабинет на 14 дней.',
     390::numeric, 14, 10, true, 'pro_1_14d', 'DAYS', 'MAIN', NULL::integer, 1, 1),
    ('Полный доступ · 1 кабинет · 1 месяц',
     'Полный доступ ко всем функциям на 1 кабинет на 1 месяц.',
     590::numeric, 30, 11, true, 'pro_1_month', 'CALENDAR_MONTH', 'MAIN', NULL::integer, 1, 1),
    -- PRO: 2–5
    ('Полный доступ · до 5 кабинетов · 14 дней',
     'Полный доступ на 2–5 кабинетов на 14 дней.',
     1490::numeric, 14, 12, true, 'pro_2_5_14d', 'DAYS', 'MAIN', NULL::integer, 2, 5),
    ('Полный доступ · до 5 кабинетов · 1 месяц',
     'Полный доступ на 2–5 кабинетов на 1 месяц.',
     2490::numeric, 30, 13, true, 'pro_2_5_month', 'CALENDAR_MONTH', 'MAIN', NULL::integer, 2, 5),
    -- PRO: 6–10
    ('Полный доступ · до 10 кабинетов · 14 дней',
     'Полный доступ на 6–10 кабинетов на 14 дней.',
     2790::numeric, 14, 14, true, 'pro_6_10_14d', 'DAYS', 'MAIN', NULL::integer, 6, 10),
    ('Полный доступ · до 10 кабинетов · 1 месяц',
     'Полный доступ на 6–10 кабинетов на 1 месяц.',
     4990::numeric, 30, 15, true, 'pro_6_10_month', 'CALENDAR_MONTH', 'MAIN', NULL::integer, 6, 10),

    -- CAMPAIGN: 1
    ('Управление РК · 1 кабинет · 14 дней',
     'Управление РК на 1 кабинет на 14 дней.',
     590::numeric, 14, 20, true, 'campaign_1_14d', 'DAYS', 'CAMPAIGN', NULL::integer, 1, 1),
    ('Управление РК · 1 кабинет · 1 месяц',
     'Управление РК на 1 кабинет на 1 месяц.',
     790::numeric, 30, 21, true, 'campaign_1_month', 'CALENDAR_MONTH', 'CAMPAIGN', NULL::integer, 1, 1),
    -- CAMPAIGN: 2–5
    ('Управление РК · до 5 кабинетов · 14 дней',
     'Управление РК на 2–5 кабинетов на 14 дней.',
     1790::numeric, 14, 22, true, 'campaign_2_5_14d', 'DAYS', 'CAMPAIGN', NULL::integer, 2, 5),
    ('Управление РК · до 5 кабинетов · 1 месяц',
     'Управление РК на 2–5 кабинетов на 1 месяц.',
     2990::numeric, 30, 23, true, 'campaign_2_5_month', 'CALENDAR_MONTH', 'CAMPAIGN', NULL::integer, 2, 5),
    -- CAMPAIGN: 6–10
    ('Управление РК · до 10 кабинетов · 14 дней',
     'Управление РК на 6–10 кабинетов на 14 дней.',
     3290::numeric, 14, 24, true, 'campaign_6_10_14d', 'DAYS', 'CAMPAIGN', NULL::integer, 6, 10),
    ('Управление РК · до 10 кабинетов · 1 месяц',
     'Управление РК на 6–10 кабинетов на 1 месяц.',
     5490::numeric, 30, 25, true, 'campaign_6_10_month', 'CALENDAR_MONTH', 'CAMPAIGN', NULL::integer, 6, 10)
) AS v(name, description, price_rub, period_days, sort_order, is_active,
       code, period_type, kind, credit_amount, min_cabinets, max_cabinets)
WHERE NOT EXISTS (SELECT 1 FROM solution.plans p WHERE p.code = v.code);

-- Актуальные цены пакетов А/Б (без срока)
UPDATE solution.plans
SET price_rub = 490, credit_amount = 1, kind = 'AB_PACK', updated_at = now()
WHERE code = 'ab_pack_1';

UPDATE solution.plans
SET price_rub = 1990, credit_amount = 5, kind = 'AB_PACK', updated_at = now()
WHERE code = 'ab_pack_5';

UPDATE solution.plans
SET price_rub = 2990, credit_amount = 10, kind = 'AB_PACK', updated_at = now()
WHERE code = 'ab_pack_10';

-- =============================================================================
-- Миграция единственного pro_month → pro_1_month (срок не меняем)
-- =============================================================================
UPDATE solution.subscriptions s
SET plan_id = p_new.id,
    updated_at = now()
FROM solution.plans p_old,
     solution.plans p_new
WHERE s.plan_id = p_old.id
  AND p_old.code = 'pro_month'
  AND p_new.code = 'pro_1_month'
  AND s.status IN ('active', 'trial')
  AND (s.expires_at IS NULL OR s.expires_at > now());

-- =============================================================================
-- Деактивация старых / trial планов (не продаём)
-- =============================================================================
UPDATE solution.plans
SET is_active = false,
    updated_at = now()
WHERE code IN (
    'pro_month',
    'campaign_free',
    'campaign_week',
    'campaign_month',
    'ab_pack_free'
)
  AND is_active IS DISTINCT FROM false;

-- analytics_free оставляем is_active=true для инициализации кабинетов, но в каталоге
-- фронт/бэк фильтруют продаваемые MAIN (price > 0 или code like pro_%).
-- Явно помечаем sort_order низким.
UPDATE solution.plans
SET sort_order = 0,
    name = 'Бесплатный доступ',
    description = 'Разделы Товары, Сводная, Рекламные кампании и Гипотезы. Платные услуги подключаются отдельно.',
    updated_at = now()
WHERE code = 'analytics_free';
