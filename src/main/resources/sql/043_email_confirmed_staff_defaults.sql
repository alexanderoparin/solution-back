-- Служебные роли: считаем почту подтверждённой (обязательное подтверждение для остальных).
UPDATE solution.users
SET email_confirmed = true
WHERE role IN ('ADMIN', 'MANAGER');
