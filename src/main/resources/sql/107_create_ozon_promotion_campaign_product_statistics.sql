-- Статистика SKU внутри рекламных кампаний Ozon Performance (async POST /api/client/statistics, groupBy=DATE).
CREATE TABLE IF NOT EXISTS solution.ozon_promotion_campaign_product_statistics (
    id              BIGSERIAL PRIMARY KEY,
    campaign_id     BIGINT NOT NULL REFERENCES solution.ozon_promotion_campaigns (campaign_id) ON DELETE CASCADE,
    sku             BIGINT NOT NULL,
    date            DATE NOT NULL,
    views           INTEGER,
    clicks          INTEGER,
    ctr             NUMERIC(10, 4),
    to_cart         INTEGER,
    avg_cpc         NUMERIC(19, 4),
    spend           NUMERIC(19, 2),
    orders          INTEGER,
    orders_money    NUMERIC(19, 2),
    model_orders    INTEGER,
    model_sales     NUMERIC(19, 2),
    drr             NUMERIC(10, 4),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ozon_promotion_campaign_product_statistics
        UNIQUE (campaign_id, sku, date)
);

COMMENT ON TABLE solution.ozon_promotion_campaign_product_statistics IS 'Дневная статистика SKU в РК Ozon (async report Performance API).';
COMMENT ON COLUMN solution.ozon_promotion_campaign_product_statistics.campaign_id IS 'ID кампании Ozon Performance';
COMMENT ON COLUMN solution.ozon_promotion_campaign_product_statistics.sku IS 'SKU товара в рекламе';
COMMENT ON COLUMN solution.ozon_promotion_campaign_product_statistics.date IS 'Дата метрик';
COMMENT ON COLUMN solution.ozon_promotion_campaign_product_statistics.views IS 'Показы';
COMMENT ON COLUMN solution.ozon_promotion_campaign_product_statistics.clicks IS 'Клики';
COMMENT ON COLUMN solution.ozon_promotion_campaign_product_statistics.ctr IS 'CTR, %';
COMMENT ON COLUMN solution.ozon_promotion_campaign_product_statistics.to_cart IS 'Добавления в корзину';
COMMENT ON COLUMN solution.ozon_promotion_campaign_product_statistics.avg_cpc IS 'Средняя цена клика, руб.';
COMMENT ON COLUMN solution.ozon_promotion_campaign_product_statistics.spend IS 'Расход, руб.';
COMMENT ON COLUMN solution.ozon_promotion_campaign_product_statistics.orders IS 'Заказы, шт.';
COMMENT ON COLUMN solution.ozon_promotion_campaign_product_statistics.orders_money IS 'Выручка, руб.';

CREATE INDEX IF NOT EXISTS idx_ozon_promo_campaign_product_stats_sku_date
    ON solution.ozon_promotion_campaign_product_statistics (sku, date);

CREATE INDEX IF NOT EXISTS idx_ozon_promo_campaign_product_stats_campaign_date
    ON solution.ozon_promotion_campaign_product_statistics (campaign_id, date);
