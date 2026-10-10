-- Удаление общего лимита активаций промокода — ограничение не используется.

ALTER TABLE solution.promo_codes
    DROP COLUMN IF EXISTS max_redemptions_total;
