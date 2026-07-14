-- Snapshot админа, обработавшего заявку (для истории независимо от удаления пользователя-админа).

ALTER TABLE solution.account_deletion_requests
    ADD COLUMN IF NOT EXISTS processed_by_email VARCHAR(255);

COMMENT ON COLUMN solution.account_deletion_requests.processed_at IS 'Дата и время обработки заявки (одобрение / отклонение). При одобрении — момент запуска удаления.';
COMMENT ON COLUMN solution.account_deletion_requests.processed_by_user_id IS 'Админ, обработавший заявку (может стать NULL, если админ удалён).';
COMMENT ON COLUMN solution.account_deletion_requests.processed_by_email IS 'Email админа на момент обработки заявки.';

UPDATE solution.account_deletion_requests adr
SET processed_by_email = COALESCE(adr.processed_by_email, u.email)
FROM solution.users u
WHERE adr.processed_by_user_id = u.id
  AND adr.processed_by_email IS NULL;
