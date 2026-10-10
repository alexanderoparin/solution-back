-- Удаление лимита активаций промокода на пользователя.
-- Повторная активация тем же пользователем всё равно запрещена unique (promo_code_id, user_id).

ALTER TABLE solution.promo_codes
    DROP COLUMN IF EXISTS max_redemptions_per_user;
