-- Тип аккаунта хранится в user_account_types; в grant дублировать не нужно.

ALTER TABLE solution.cabinet_access_grants
    DROP COLUMN IF EXISTS account_type;
