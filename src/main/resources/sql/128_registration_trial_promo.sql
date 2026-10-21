-- Системный промокод для автоматического пробного PRO при регистрации без промокода.
-- Длительность в коде берётся из subscription.trial-days; duration_days — fallback / отображение.

INSERT INTO solution.promo_codes (code, description, duration_days, active)
VALUES (
    'REGISTRATION_TRIAL',
    'Автоматический пробный период при регистрации (не для ручного ввода)',
    7,
    TRUE
)
ON CONFLICT (code) DO UPDATE
SET description = EXCLUDED.description,
    duration_days = EXCLUDED.duration_days,
    active = TRUE;
