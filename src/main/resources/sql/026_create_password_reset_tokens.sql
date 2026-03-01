-- Токены для сброса пароля (восстановление по email).
CREATE TABLE IF NOT EXISTS solution.password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES solution.users(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_password_reset_token UNIQUE (token)
);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_token ON solution.password_reset_tokens(token);
CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_user_id ON solution.password_reset_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_expires_at ON solution.password_reset_tokens(expires_at);

COMMENT ON TABLE solution.password_reset_tokens IS 'Токены для сброса пароля по ссылке из письма';
COMMENT ON COLUMN solution.password_reset_tokens.user_id IS 'Пользователь, для которого выдан токен';
COMMENT ON COLUMN solution.password_reset_tokens.token IS 'Уникальный токен (в ссылке)';
COMMENT ON COLUMN solution.password_reset_tokens.expires_at IS 'Момент истечения токена';
