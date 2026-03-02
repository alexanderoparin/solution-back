-- Назначение платежа (описание, передаётся в Робокассу и сохраняется для истории).
ALTER TABLE solution.payments
    ADD COLUMN IF NOT EXISTS description TEXT;

COMMENT ON COLUMN solution.payments.description IS 'Назначение платежа (описание для платёжной системы и отображения)';
