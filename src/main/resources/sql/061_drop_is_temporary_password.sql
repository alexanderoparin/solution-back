-- Удаление флага временного пароля: смена пароля при первом входе больше не используется.

DROP INDEX IF EXISTS solution.idx_users_is_temporary_password;

ALTER TABLE solution.users
    DROP COLUMN IF EXISTS is_temporary_password;
