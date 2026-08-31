-- Промокоды и активации (фокус-группа и будущие акции).

CREATE TABLE IF NOT EXISTS solution.promo_codes (
    id                      BIGSERIAL PRIMARY KEY,
    code                    VARCHAR(64) NOT NULL,
    description             TEXT,
    duration_days           INT NOT NULL,
    grant_type              VARCHAR(32) NOT NULL,
    active                  BOOLEAN NOT NULL DEFAULT TRUE,
    max_redemptions_per_user INT NOT NULL DEFAULT 1,
    max_redemptions_total   INT,
    valid_from              TIMESTAMP,
    valid_to                TIMESTAMP,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_promo_codes_code UNIQUE (code)
);

COMMENT ON TABLE solution.promo_codes IS 'Справочник промокодов';
COMMENT ON COLUMN solution.promo_codes.code IS 'Код промо (хранится в верхнем регистре)';
COMMENT ON COLUMN solution.promo_codes.duration_days IS 'Срок доступа в днях с момента активации';
COMMENT ON COLUMN solution.promo_codes.grant_type IS 'Тип доступа: FULL_ACCESS и др.';
COMMENT ON COLUMN solution.promo_codes.active IS 'Промокод доступен для активации';
COMMENT ON COLUMN solution.promo_codes.max_redemptions_per_user IS 'Лимит активаций на одного пользователя';
COMMENT ON COLUMN solution.promo_codes.max_redemptions_total IS 'Общий лимит активаций (NULL — без лимита)';
COMMENT ON COLUMN solution.promo_codes.valid_from IS 'Начало периода, когда код можно ввести';
COMMENT ON COLUMN solution.promo_codes.valid_to IS 'Конец периода, когда код можно ввести';

CREATE TABLE IF NOT EXISTS solution.promo_code_redemptions (
    id              BIGSERIAL PRIMARY KEY,
    promo_code_id   BIGINT NOT NULL REFERENCES solution.promo_codes (id),
    user_id         BIGINT NOT NULL REFERENCES solution.users (id),
    redeemed_at     TIMESTAMP NOT NULL,
    expires_at      TIMESTAMP NOT NULL,
    source          VARCHAR(32) NOT NULL,
    CONSTRAINT uk_promo_code_redemptions_user_promo UNIQUE (promo_code_id, user_id)
);

COMMENT ON TABLE solution.promo_code_redemptions IS 'Факты активации промокодов пользователями';
COMMENT ON COLUMN solution.promo_code_redemptions.redeemed_at IS 'Дата и время активации';
COMMENT ON COLUMN solution.promo_code_redemptions.expires_at IS 'Дата и время окончания доступа по промо';
COMMENT ON COLUMN solution.promo_code_redemptions.source IS 'Контекст активации: REGISTRATION, PROFILE, ADMIN';

CREATE INDEX IF NOT EXISTS idx_promo_code_redemptions_user_id ON solution.promo_code_redemptions (user_id);
CREATE INDEX IF NOT EXISTS idx_promo_code_redemptions_expires_at ON solution.promo_code_redemptions (expires_at);

INSERT INTO solution.promo_codes (code, description, duration_days, grant_type, active)
VALUES (
    'FOCUS',
    'Фокус-группа менеджеров: полный доступ 14 дней',
    14,
    'FULL_ACCESS',
    TRUE
)
ON CONFLICT (code) DO NOTHING;
