-- Удаление seller_id: владелец определяется через cabinet_id → cabinets.user_id.
-- Выполнять после того, как у всех строк заполнен cabinet_id (миграция 017).

ALTER TABLE solution.product_cards
    DROP COLUMN IF EXISTS seller_id;

ALTER TABLE solution.promotion_campaigns
    DROP COLUMN IF EXISTS seller_id;
