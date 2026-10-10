-- Удаление типа доступа промокода: все промокоды дают полный доступ.

ALTER TABLE solution.promo_codes DROP COLUMN IF EXISTS grant_type;
