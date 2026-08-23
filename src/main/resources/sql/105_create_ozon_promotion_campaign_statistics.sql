-- Ежедневная статистика рекламных кампаний Ozon Performance API (GET /api/client/statistics/daily/json).

CREATE TABLE IF NOT EXISTS solution.ozon_promotion_campaign_statistics (
    id BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL REFERENCES solution.ozon_promotion_campaigns(campaign_id) ON DELETE CASCADE,
    date DATE NOT NULL,
    views INTEGER,
    clicks INTEGER,
    spend NUMERIC(19, 2),
    avg_bid NUMERIC(19, 4),
    orders INTEGER,
    orders_money NUMERIC(19, 2),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ozon_promotion_campaign_statistics_campaign_date
        UNIQUE (campaign_id, date)
);

COMMENT ON TABLE solution.ozon_promotion_campaign_statistics IS 'Ежедневная статистика РК Ozon (показы, клики, расход, заказы).';
COMMENT ON COLUMN solution.ozon_promotion_campaign_statistics.campaign_id IS 'ID кампании Ozon Performance';
COMMENT ON COLUMN solution.ozon_promotion_campaign_statistics.date IS 'Дата метрик';
COMMENT ON COLUMN solution.ozon_promotion_campaign_statistics.views IS 'Показы';
COMMENT ON COLUMN solution.ozon_promotion_campaign_statistics.clicks IS 'Клики';
COMMENT ON COLUMN solution.ozon_promotion_campaign_statistics.spend IS 'Расход, руб.';
COMMENT ON COLUMN solution.ozon_promotion_campaign_statistics.avg_bid IS 'Средняя ставка, руб.';
COMMENT ON COLUMN solution.ozon_promotion_campaign_statistics.orders IS 'Заказы, шт.';
COMMENT ON COLUMN solution.ozon_promotion_campaign_statistics.orders_money IS 'Сумма заказов, руб.';

CREATE INDEX IF NOT EXISTS idx_ozon_promo_stats_campaign_date
    ON solution.ozon_promotion_campaign_statistics (campaign_id, date);
