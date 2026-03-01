-- Токены для подтверждения почты (ссылка в письме).
CREATE TABLE IF NOT EXISTS solution.email_confirmation_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES solution.users(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_email_confirmation_token UNIQUE (token)
);

CREATE INDEX IF NOT EXISTS idx_email_confirmation_tokens_token ON solution.email_confirmation_tokens(token);
CREATE INDEX IF NOT EXISTS idx_email_confirmation_tokens_user_id ON solution.email_confirmation_tokens(user_id);

COMMENT ON TABLE solution.email_confirmation_tokens IS 'Токены для подтверждения email по ссылке из письма';

-- Когда последний раз отправляли письмо с подтверждением (ограничение: не чаще 1 раза в 24 ч).
ALTER TABLE solution.users
  ADD COLUMN IF NOT EXISTS last_email_confirmation_sent_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN solution.users.last_email_confirmation_sent_at IS 'Дата последней отправки письма для подтверждения почты (повтор не чаще 1 раза в 24 ч)';
