-- Добавление cabinet_id в product_cards, promotion_campaigns, article_notes.
-- Выполнять после 016_create_cabinets_migrate_from_wb_api_keys.sql (схема solution).

-- 1. product_cards: добавить cabinet_id
ALTER TABLE solution.product_cards
    ADD COLUMN IF NOT EXISTS cabinet_id BIGINT REFERENCES solution.cabinets(id) ON DELETE CASCADE;

UPDATE solution.product_cards pc
SET cabinet_id = (SELECT c.id FROM solution.cabinets c WHERE c.user_id = pc.seller_id ORDER BY c.created_at DESC LIMIT 1)
WHERE cabinet_id IS NULL;

ALTER TABLE solution.product_cards
    ALTER COLUMN cabinet_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_product_cards_nm_id_cabinet_id
    ON solution.product_cards(nm_id, cabinet_id);

-- 2. promotion_campaigns: добавить cabinet_id
ALTER TABLE solution.promotion_campaigns
    ADD COLUMN IF NOT EXISTS cabinet_id BIGINT REFERENCES solution.cabinets(id) ON DELETE CASCADE;

UPDATE solution.promotion_campaigns pc
SET cabinet_id = (SELECT c.id FROM solution.cabinets c WHERE c.user_id = pc.seller_id ORDER BY c.created_at DESC LIMIT 1)
WHERE cabinet_id IS NULL;

ALTER TABLE solution.promotion_campaigns
    ALTER COLUMN cabinet_id SET NOT NULL;

-- 3. article_notes: добавить cabinet_id
ALTER TABLE solution.article_notes
    ADD COLUMN IF NOT EXISTS cabinet_id BIGINT REFERENCES solution.cabinets(id) ON DELETE CASCADE;

UPDATE solution.article_notes an
SET cabinet_id = (SELECT c.id FROM solution.cabinets c WHERE c.user_id = an.seller_id ORDER BY c.created_at DESC LIMIT 1)
WHERE cabinet_id IS NULL;

ALTER TABLE solution.article_notes
    ALTER COLUMN cabinet_id SET NOT NULL;
