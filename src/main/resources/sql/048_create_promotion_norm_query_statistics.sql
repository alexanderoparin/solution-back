-- Статистика по поисковым кластерам (norm query) из POST /adv/v1/normquery/stats
CREATE TABLE IF NOT EXISTS solution.promotion_norm_query_statistics (
    id BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL REFERENCES solution.promotion_campaigns(advert_id) ON DELETE CASCADE,
    nm_id BIGINT NOT NULL,
    date DATE NOT NULL,
    norm_query TEXT NOT NULL,
    avg_pos NUMERIC(12, 4),
    clicks INTEGER,
    atbs INTEGER,
    orders INTEGER,
    shks INTEGER,
    spend NUMERIC(14, 2),
    cpc NUMERIC(12, 4),
    views INTEGER,
    ctr NUMERIC(10, 4),
    cpm NUMERIC(12, 4),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (campaign_id, nm_id, date, norm_query)
);

COMMENT ON TABLE solution.promotion_norm_query_statistics IS 'Статистика по поисковым кластерам WB (POST /adv/v1/normquery/stats) по кампании, артикулу и дате.';
COMMENT ON COLUMN solution.promotion_norm_query_statistics.id IS 'ID записи';
COMMENT ON COLUMN solution.promotion_norm_query_statistics.campaign_id IS 'ID рекламной кампании (advert_id)';
COMMENT ON COLUMN solution.promotion_norm_query_statistics.nm_id IS 'Артикул WB (nmId)';
COMMENT ON COLUMN solution.promotion_norm_query_statistics.date IS 'Дата статистики';
COMMENT ON COLUMN solution.promotion_norm_query_statistics.norm_query IS 'Поисковый кластер (поисковая фраза)';
COMMENT ON COLUMN solution.promotion_norm_query_statistics.avg_pos IS 'Средняя позиция в поисковой выдаче';
COMMENT ON COLUMN solution.promotion_norm_query_statistics.clicks IS 'Клики';
COMMENT ON COLUMN solution.promotion_norm_query_statistics.atbs IS 'Добавления в корзину';
COMMENT ON COLUMN solution.promotion_norm_query_statistics.orders IS 'Заказы';
COMMENT ON COLUMN solution.promotion_norm_query_statistics.shks IS 'Заказано товаров, шт';
COMMENT ON COLUMN solution.promotion_norm_query_statistics.spend IS 'Затраты, руб';
COMMENT ON COLUMN solution.promotion_norm_query_statistics.cpc IS 'Средняя стоимость клика, руб';
COMMENT ON COLUMN solution.promotion_norm_query_statistics.views IS 'Просмотры (для CPC может быть null)';
COMMENT ON COLUMN solution.promotion_norm_query_statistics.ctr IS 'CTR, % (для CPC может быть null)';
COMMENT ON COLUMN solution.promotion_norm_query_statistics.cpm IS 'CPM, руб (для CPC может быть null)';
COMMENT ON COLUMN solution.promotion_norm_query_statistics.created_at IS 'Дата создания записи в БД';
COMMENT ON COLUMN solution.promotion_norm_query_statistics.updated_at IS 'Дата обновления записи в БД';
