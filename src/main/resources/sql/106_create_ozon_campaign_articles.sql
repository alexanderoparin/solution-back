-- Связь рекламных кампаний Ozon с SKU (объекты / товары в РК).

CREATE TABLE IF NOT EXISTS solution.ozon_campaign_articles (
    campaign_id BIGINT NOT NULL REFERENCES solution.ozon_promotion_campaigns(campaign_id) ON DELETE CASCADE,
    sku BIGINT NOT NULL,
    product_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (campaign_id, sku)
);

COMMENT ON TABLE solution.ozon_campaign_articles IS 'Связь РК Ozon Performance с SKU (из /campaign/{id}/objects или /v2/products).';
COMMENT ON COLUMN solution.ozon_campaign_articles.campaign_id IS 'ID кампании Ozon';
COMMENT ON COLUMN solution.ozon_campaign_articles.sku IS 'SKU товара в рекламе';
COMMENT ON COLUMN solution.ozon_campaign_articles.product_id IS 'product_id из ozon_product_cards (если удалось сопоставить по sku)';

CREATE INDEX IF NOT EXISTS idx_ozon_campaign_articles_sku
    ON solution.ozon_campaign_articles (sku);

CREATE INDEX IF NOT EXISTS idx_ozon_campaign_articles_product
    ON solution.ozon_campaign_articles (product_id);

CREATE INDEX IF NOT EXISTS idx_ozon_campaign_articles_campaign
    ON solution.ozon_campaign_articles (campaign_id);
