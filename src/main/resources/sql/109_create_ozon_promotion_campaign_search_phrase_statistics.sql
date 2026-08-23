-- Статистика по поисковым запросам в РК Ozon Performance (async POST /api/client/statistics/phrases).
CREATE TABLE IF NOT EXISTS solution.ozon_promotion_campaign_search_phrase_statistics (
    id              BIGSERIAL PRIMARY KEY,
    campaign_id     BIGINT NOT NULL REFERENCES solution.ozon_promotion_campaigns (campaign_id) ON DELETE CASCADE,
    sku             BIGINT,
    date            DATE NOT NULL,
    search_phrase   TEXT NOT NULL,
    avg_pos         NUMERIC(12, 4),
    views           INTEGER,
    clicks          INTEGER,
    ctr             NUMERIC(10, 4),
    to_cart         INTEGER,
    avg_cpc         NUMERIC(19, 4),
    spend           NUMERIC(19, 2),
    orders          INTEGER,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ozon_promotion_campaign_search_phrase_statistics
        UNIQUE (campaign_id, date, search_phrase)
);

COMMENT ON TABLE solution.ozon_promotion_campaign_search_phrase_statistics IS 'Поисковые запросы (кластеры) в РК Ozon — async-отчёт Performance API /statistics/phrases.';
COMMENT ON COLUMN solution.ozon_promotion_campaign_search_phrase_statistics.campaign_id IS 'ID кампании Ozon Performance';
COMMENT ON COLUMN solution.ozon_promotion_campaign_search_phrase_statistics.sku IS 'SKU товара (если есть в отчёте)';
COMMENT ON COLUMN solution.ozon_promotion_campaign_search_phrase_statistics.date IS 'Дата метрик';
COMMENT ON COLUMN solution.ozon_promotion_campaign_search_phrase_statistics.search_phrase IS 'Поисковый запрос (фраза)';
COMMENT ON COLUMN solution.ozon_promotion_campaign_search_phrase_statistics.avg_pos IS 'Средняя позиция в выдаче';
COMMENT ON COLUMN solution.ozon_promotion_campaign_search_phrase_statistics.views IS 'Показы';
COMMENT ON COLUMN solution.ozon_promotion_campaign_search_phrase_statistics.clicks IS 'Клики';
COMMENT ON COLUMN solution.ozon_promotion_campaign_search_phrase_statistics.to_cart IS 'Добавления в корзину';
COMMENT ON COLUMN solution.ozon_promotion_campaign_search_phrase_statistics.spend IS 'Расход, руб.';

CREATE INDEX IF NOT EXISTS idx_ozon_promo_search_phrase_stats_campaign_date
    ON solution.ozon_promotion_campaign_search_phrase_statistics (campaign_id, date);
