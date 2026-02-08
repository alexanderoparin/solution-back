-- Таблица для связи рекламных кампаний и артикулов
CREATE TABLE IF NOT EXISTS solution.campaign_articles (
    campaign_id BIGINT NOT NULL,
    nm_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (campaign_id, nm_id),
    CONSTRAINT fk_campaign_articles_campaign 
        FOREIGN KEY (campaign_id) 
        REFERENCES solution.promotion_campaigns(advert_id) 
        ON DELETE CASCADE,
    CONSTRAINT fk_campaign_articles_product_card 
        FOREIGN KEY (nm_id) 
        REFERENCES solution.product_cards(nm_id) 
        ON DELETE CASCADE
);

COMMENT ON TABLE solution.campaign_articles IS 'Связь рекламных кампаний и артикулов';
COMMENT ON COLUMN solution.campaign_articles.campaign_id IS 'ID рекламной кампании';
COMMENT ON COLUMN solution.campaign_articles.nm_id IS 'Артикул товара (nmID)';

