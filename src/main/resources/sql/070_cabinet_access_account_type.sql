-- Тип аккаунта при приглашении: применяется к user_account_types после принятия.

ALTER TABLE solution.cabinet_access_invitations
    ADD COLUMN IF NOT EXISTS account_type VARCHAR(20);

UPDATE solution.cabinet_access_invitations
SET account_type = 'EMPLOYEE'
WHERE account_type IS NULL;

ALTER TABLE solution.cabinet_access_invitations
    ALTER COLUMN account_type SET NOT NULL;

ALTER TABLE solution.cabinet_access_invitations
    DROP CONSTRAINT IF EXISTS cabinet_access_invitations_account_type_check;

ALTER TABLE solution.cabinet_access_invitations
    ADD CONSTRAINT cabinet_access_invitations_account_type_check
        CHECK (account_type IN ('AGENCY', 'EMPLOYEE'));

COMMENT ON COLUMN solution.cabinet_access_invitations.account_type IS 'Тип аккаунта для добавления в профиль при принятии приглашения: AGENCY или EMPLOYEE.';
