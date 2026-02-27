-- Подписка и оплата: планы, подписки, платежи.
-- Используется для проверки доступа (подписка или селлер агентства по users.owner_id).

-- 1. Таблица тарифов (планов)
CREATE TABLE IF NOT EXISTS solution.plans (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price_rub NUMERIC(10, 2) NOT NULL,
    period_days INTEGER NOT NULL,
    max_cabinets INTEGER,
    sort_order INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_plans_is_active ON solution.plans(is_active) WHERE is_active = true;

COMMENT ON TABLE solution.plans IS 'Тарифные планы подписки';
COMMENT ON COLUMN solution.plans.name IS 'Название плана для отображения в ЛК';
COMMENT ON COLUMN solution.plans.description IS 'Описание тарифа';
COMMENT ON COLUMN solution.plans.price_rub IS 'Цена в рублях';
COMMENT ON COLUMN solution.plans.period_days IS 'Длительность периода в днях (30, 365 и т.д.)';
COMMENT ON COLUMN solution.plans.max_cabinets IS 'Лимит кабинетов (если задан)';
COMMENT ON COLUMN solution.plans.sort_order IS 'Порядок отображения в списке планов';
COMMENT ON COLUMN solution.plans.is_active IS 'Доступен ли план для выбора';

-- 2. Таблица подписок
CREATE TABLE IF NOT EXISTS solution.subscriptions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES solution.users(id) ON DELETE CASCADE,
    plan_id BIGINT REFERENCES solution.plans(id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_subscription_status CHECK (status IN ('active', 'expired', 'cancelled', 'trial'))
);

CREATE INDEX IF NOT EXISTS idx_subscriptions_user_id ON solution.subscriptions(user_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_user_status_expires
    ON solution.subscriptions(user_id, status, expires_at);

COMMENT ON TABLE solution.subscriptions IS 'Подписки пользователей (в т.ч. триал)';
COMMENT ON COLUMN solution.subscriptions.user_id IS 'Пользователь';
COMMENT ON COLUMN solution.subscriptions.plan_id IS 'План (null для триала)';
COMMENT ON COLUMN solution.subscriptions.status IS 'active, expired, cancelled, trial';
COMMENT ON COLUMN solution.subscriptions.started_at IS 'Начало периода';
COMMENT ON COLUMN solution.subscriptions.expires_at IS 'Окончание периода';

-- 3. Таблица платежей
CREATE TABLE IF NOT EXISTS solution.payments (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES solution.users(id) ON DELETE CASCADE,
    subscription_id BIGINT REFERENCES solution.subscriptions(id) ON DELETE SET NULL,
    amount NUMERIC(10, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    external_id VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata JSONB,
    CONSTRAINT chk_payment_status CHECK (status IN ('pending', 'success', 'failed', 'refunded'))
);

CREATE INDEX IF NOT EXISTS idx_payments_user_id ON solution.payments(user_id);
CREATE INDEX IF NOT EXISTS idx_payments_external_id ON solution.payments(external_id) WHERE external_id IS NOT NULL;

COMMENT ON TABLE solution.payments IS 'Платежи (Робокасса и др.)';
COMMENT ON COLUMN solution.payments.user_id IS 'Пользователь';
COMMENT ON COLUMN solution.payments.subscription_id IS 'Подписка, которую оплатили (после успеха)';
COMMENT ON COLUMN solution.payments.amount IS 'Сумма';
COMMENT ON COLUMN solution.payments.currency IS 'Валюта (RUB)';
COMMENT ON COLUMN solution.payments.external_id IS 'Идентификатор в платёжной системе (Робокасса)';
COMMENT ON COLUMN solution.payments.status IS 'pending, success, failed, refunded';
COMMENT ON COLUMN solution.payments.paid_at IS 'Фактическая дата/время оплаты';
COMMENT ON COLUMN solution.payments.metadata IS 'Параметры/ответ от платёжной системы';
