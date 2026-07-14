-- Рефакторинг ролей: ADMIN/USER, типы аккаунта, доступы к кабинетам, заявки на удаление.

-- 1. Имя пользователя
ALTER TABLE solution.users ADD COLUMN IF NOT EXISTS name VARCHAR(255);
COMMENT ON COLUMN solution.users.name IS 'Отображаемое имя пользователя';

-- 2. Миграция ролей MANAGER/SELLER/WORKER -> USER
ALTER TABLE solution.users DROP CONSTRAINT IF EXISTS users_role_check;
UPDATE solution.users SET role = 'USER' WHERE role IN ('MANAGER', 'SELLER', 'WORKER');
ALTER TABLE solution.users ADD CONSTRAINT users_role_check CHECK (role IN ('ADMIN', 'USER'));

-- 3. Типы аккаунта (статистика, не права)
CREATE TABLE IF NOT EXISTS solution.user_account_types (
    user_id BIGINT NOT NULL REFERENCES solution.users(id) ON DELETE CASCADE,
    account_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, account_type),
    CONSTRAINT user_account_types_type_check CHECK (account_type IN ('SELLER', 'AGENCY', 'EMPLOYEE'))
);

COMMENT ON TABLE solution.user_account_types IS 'Номинальные типы аккаунта пользователя (может быть несколько).';
COMMENT ON COLUMN solution.user_account_types.account_type IS 'SELLER — продавец, AGENCY — агентство, EMPLOYEE — сотрудник.';

-- Миграция типов из старых ролей (до смены role на USER)
INSERT INTO solution.user_account_types (user_id, account_type)
SELECT id, 'SELLER' FROM solution.users u
WHERE EXISTS (
    SELECT 1 FROM solution.cabinets c WHERE c.user_id = u.id
) OR u.agency_managed = true
ON CONFLICT DO NOTHING;

INSERT INTO solution.user_account_types (user_id, account_type)
SELECT DISTINCT sma.manager_id, 'AGENCY'
FROM solution.seller_manager_access sma
WHERE sma.status = 'ACTIVE'
ON CONFLICT DO NOTHING;

INSERT INTO solution.user_account_types (user_id, account_type)
SELECT DISTINCT sw.worker_id, 'EMPLOYEE'
FROM solution.seller_worker sw
ON CONFLICT DO NOTHING;

INSERT INTO solution.user_account_types (user_id, account_type)
SELECT id, 'SELLER' FROM solution.users
WHERE id NOT IN (SELECT user_id FROM solution.user_account_types)
  AND role = 'USER'
ON CONFLICT DO NOTHING;

-- 4. Доступы к кабинетам (grants)
CREATE TABLE IF NOT EXISTS solution.cabinet_access_grants (
    id BIGSERIAL PRIMARY KEY,
    cabinet_id BIGINT NOT NULL REFERENCES solution.cabinets(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES solution.users(id) ON DELETE CASCADE,
    sections JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    comment_text VARCHAR(500),
    valid_from TIMESTAMP NOT NULL DEFAULT NOW(),
    valid_until TIMESTAMP,
    granted_by_user_id BIGINT REFERENCES solution.users(id) ON DELETE SET NULL,
    invitation_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMP,
    CONSTRAINT cabinet_access_grants_status_check CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT uq_cabinet_access_grants UNIQUE (cabinet_id, user_id)
);

COMMENT ON TABLE solution.cabinet_access_grants IS 'Выданный доступ пользователя к кабинету по разделам.';
COMMENT ON COLUMN solution.cabinet_access_grants.sections IS 'JSON-массив разделов: PRODUCTS, SUMMARY, AD_CAMPAIGNS, CAMPAIGN_MANAGE.';
COMMENT ON COLUMN solution.cabinet_access_grants.comment_text IS 'Комментарий владельца (виден только ему).';

CREATE INDEX IF NOT EXISTS idx_cabinet_access_grants_user_status
    ON solution.cabinet_access_grants (user_id, status);
CREATE INDEX IF NOT EXISTS idx_cabinet_access_grants_cabinet_status
    ON solution.cabinet_access_grants (cabinet_id, status);

-- 5. Приглашения
CREATE TABLE IF NOT EXISTS solution.cabinet_access_invitations (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(64) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL,
    cabinet_id BIGINT NOT NULL REFERENCES solution.cabinets(id) ON DELETE CASCADE,
    invited_by_user_id BIGINT NOT NULL REFERENCES solution.users(id) ON DELETE CASCADE,
    sections JSONB NOT NULL DEFAULT '[]'::jsonb,
    comment_text VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    valid_until TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    accepted_at TIMESTAMP,
    accepted_by_user_id BIGINT REFERENCES solution.users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT cabinet_access_invitations_status_check CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED'))
);

COMMENT ON TABLE solution.cabinet_access_invitations IS 'Приглашение пользователя в кабинет по email.';
COMMENT ON COLUMN solution.cabinet_access_invitations.token IS 'Уникальный токен ссылки приглашения.';

CREATE INDEX IF NOT EXISTS idx_cabinet_access_invitations_cabinet_status
    ON solution.cabinet_access_invitations (cabinet_id, status);
CREATE INDEX IF NOT EXISTS idx_cabinet_access_invitations_email_pending
    ON solution.cabinet_access_invitations (email, status) WHERE status = 'PENDING';

ALTER TABLE solution.cabinet_access_grants
    ADD CONSTRAINT fk_cabinet_access_grants_invitation
    FOREIGN KEY (invitation_id) REFERENCES solution.cabinet_access_invitations(id) ON DELETE SET NULL;

-- Миграция seller_manager_access -> grants по каждому кабинету
INSERT INTO solution.cabinet_access_grants (
    cabinet_id, user_id, sections, status, valid_from, granted_by_user_id, created_at, updated_at
)
SELECT
    c.id,
    sma.manager_id,
    '["PRODUCTS","SUMMARY","AD_CAMPAIGNS","CAMPAIGN_MANAGE"]'::jsonb,
    'ACTIVE',
    COALESCE(sma.granted_at, NOW()),
    sma.seller_id,
    COALESCE(sma.granted_at, NOW()),
    NOW()
FROM solution.seller_manager_access sma
JOIN solution.cabinets c ON c.user_id = sma.seller_id
WHERE sma.status = 'ACTIVE'
ON CONFLICT (cabinet_id, user_id) DO NOTHING;

-- Миграция seller_worker -> grants
INSERT INTO solution.cabinet_access_grants (
    cabinet_id, user_id, sections, status, valid_from, granted_by_user_id, created_at, updated_at
)
SELECT
    c.id,
    sw.worker_id,
    '["PRODUCTS","SUMMARY","AD_CAMPAIGNS"]'::jsonb,
    'ACTIVE',
    COALESCE(sw.created_at, NOW()),
    sw.seller_id,
    COALESCE(sw.created_at, NOW()),
    NOW()
FROM solution.seller_worker sw
JOIN solution.cabinets c ON c.user_id = sw.seller_id
ON CONFLICT (cabinet_id, user_id) DO NOTHING;

-- 6. Заявки на удаление аккаунта
CREATE TABLE IF NOT EXISTS solution.account_deletion_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES solution.users(id) ON DELETE CASCADE,
    reason VARCHAR(50) NOT NULL,
    comment_text TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP,
    processed_by_user_id BIGINT REFERENCES solution.users(id) ON DELETE SET NULL,
    CONSTRAINT account_deletion_requests_status_check CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

COMMENT ON TABLE solution.account_deletion_requests IS 'Заявки пользователей на удаление профиля.';
CREATE UNIQUE INDEX IF NOT EXISTS uq_account_deletion_requests_user_pending
    ON solution.account_deletion_requests (user_id) WHERE status = 'PENDING';

-- 7. Бесплатный тариф аналитики
INSERT INTO solution.plans (name, description, price_rub, period_days, code, sort_order, is_active)
SELECT
    'Бесплатный доступ',
    'Товары, Сводная и Рекламные кампании',
    0.00,
    365,
    'analytics_free',
    0,
    true
WHERE NOT EXISTS (SELECT 1 FROM solution.plans WHERE code = 'analytics_free');

-- 8. Автопродление подписки (UI)
ALTER TABLE solution.subscriptions ADD COLUMN IF NOT EXISTS auto_renew BOOLEAN NOT NULL DEFAULT false;
COMMENT ON COLUMN solution.subscriptions.auto_renew IS 'Автопродление (для UI; платёжная логика отдельно).';

-- Бесплатная подписка для существующих USER без активной подписки
INSERT INTO solution.subscriptions (user_id, plan_id, status, started_at, expires_at, auto_renew, created_at, updated_at)
SELECT
    u.id,
    p.id,
    'active',
    COALESCE(u.created_at, NOW()),
    NULL,
    true,
    NOW(),
    NOW()
FROM solution.users u
JOIN solution.plans p ON p.code = 'analytics_free'
WHERE u.role = 'USER'
  AND NOT EXISTS (
      SELECT 1 FROM solution.subscriptions s
      WHERE s.user_id = u.id
        AND s.status IN ('active', 'trial')
        AND (s.expires_at IS NULL OR s.expires_at > NOW())
  );
