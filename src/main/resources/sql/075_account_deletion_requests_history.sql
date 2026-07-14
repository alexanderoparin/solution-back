-- Сохранение истории заявок на удаление после удаления пользователя.
-- Snapshot email/name + ON DELETE SET NULL вместо CASCADE.

ALTER TABLE solution.account_deletion_requests
    ADD COLUMN IF NOT EXISTS user_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS user_name VARCHAR(255);

COMMENT ON COLUMN solution.account_deletion_requests.user_email IS 'Email пользователя на момент заявки (сохраняется после удаления аккаунта).';
COMMENT ON COLUMN solution.account_deletion_requests.user_name IS 'Имя пользователя на момент заявки (сохраняется после удаления аккаунта).';

-- Заполнить snapshot из текущих пользователей, где ещё есть связь
UPDATE solution.account_deletion_requests adr
SET
    user_email = COALESCE(adr.user_email, u.email),
    user_name = COALESCE(adr.user_name, u.name)
FROM solution.users u
WHERE adr.user_id = u.id
  AND (adr.user_email IS NULL OR adr.user_name IS NULL);

-- На случай «висячих» записей без пользователя (маловероятно при CASCADE)
UPDATE solution.account_deletion_requests
SET user_email = COALESCE(user_email, 'unknown@deleted')
WHERE user_email IS NULL;

ALTER TABLE solution.account_deletion_requests
    ALTER COLUMN user_email SET NOT NULL;

ALTER TABLE solution.account_deletion_requests
    DROP CONSTRAINT IF EXISTS account_deletion_requests_user_id_fkey;

ALTER TABLE solution.account_deletion_requests
    ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE solution.account_deletion_requests
    ADD CONSTRAINT account_deletion_requests_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES solution.users(id) ON DELETE SET NULL;
