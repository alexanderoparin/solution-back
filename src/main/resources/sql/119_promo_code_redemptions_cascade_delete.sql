-- Каскадное удаление активаций промокодов при удалении пользователя.
-- Ранее отсутствие ON DELETE CASCADE приводило к ошибке foreign key (23503) при удалении записи пользователя из solution.users.

ALTER TABLE solution.promo_code_redemptions
    DROP CONSTRAINT IF EXISTS promo_code_redemptions_user_id_fkey;

ALTER TABLE solution.promo_code_redemptions
    ADD CONSTRAINT promo_code_redemptions_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES solution.users (id) ON DELETE CASCADE;

COMMENT ON CONSTRAINT promo_code_redemptions_user_id_fkey ON solution.promo_code_redemptions IS
    'Каскадное удаление записей об активации промокодов при удалении аккаунта пользователя';
