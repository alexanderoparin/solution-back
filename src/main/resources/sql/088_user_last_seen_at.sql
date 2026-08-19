ALTER TABLE solution.users
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMP NULL;

COMMENT ON COLUMN solution.users.last_seen_at IS
    'Дата и время последней активности пользователя по авторизованному запросу; обновляется с throttling.';
