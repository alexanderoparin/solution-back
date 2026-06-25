-- Дата и время появления карточки на WB (поле createdAt из /content/v2/get/cards/list).

ALTER TABLE solution.product_cards
    ADD COLUMN IF NOT EXISTS wb_created_at TIMESTAMP;

UPDATE solution.product_cards
SET wb_created_at = created_at
WHERE wb_created_at IS NULL;

ALTER TABLE solution.product_cards
    ALTER COLUMN wb_created_at DROP DEFAULT;

ALTER TABLE solution.product_cards
    ALTER COLUMN wb_created_at SET NOT NULL;

COMMENT ON COLUMN solution.product_cards.wb_created_at IS
    'Дата и время создания карточки на Wildberries (createdAt из WB Content API)';
