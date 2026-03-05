-- Согласие на маркетинговые сообщения (при регистрации)
ALTER TABLE solution.users ADD COLUMN IF NOT EXISTS marketing_consent boolean NOT NULL DEFAULT false;
COMMENT ON COLUMN solution.users.marketing_consent IS 'Согласие на получение информационных и маркетинговых сообщений (при регистрации)';
