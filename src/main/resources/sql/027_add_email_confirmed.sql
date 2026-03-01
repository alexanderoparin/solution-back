-- Подтверждение почты: только для сторонних селлеров; true только после явного подтверждения (ссылка в письме).
-- Селлерам из агентства подтверждение не требуется (поле не используется).
ALTER TABLE solution.users
  ADD COLUMN IF NOT EXISTS email_confirmed BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN solution.users.email_confirmed IS 'Почта подтверждена; true только после явного подтверждения. Для клиентов агентства не используется.';

-- Если колонка уже была добавлена с DEFAULT true, сбросить всем: UPDATE solution.users SET email_confirmed = false;
