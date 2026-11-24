-- Статистика РК WB с разрезами placement / conversion_type (ответ GET /api/advert/v4/fullstats).
-- Исторические данные из wb_promotion_campaign_statistics переносятся со значениями
-- placement = 'all', conversion_type = 'all' (итог без разбивки, данные v3).

CREATE TABLE IF NOT EXISTS solution.wb_promotion_campaign_statistics_slices (
    id BIGSERIAL PRIMARY KEY,
    -- ID кампании (advert_id)
    campaign_id BIGINT NOT NULL
        REFERENCES solution.wb_promotion_campaigns(advert_id) ON DELETE CASCADE,
    -- Артикул WB (nmId)
    nm_id BIGINT NOT NULL,
    -- Дата статистики
    date DATE NOT NULL,
    -- Зона показов: search | recommendation | all (история без разреза)
    placement VARCHAR(32) NOT NULL,
    -- Тип конверсии: direct | multicard | associated | all (история без разреза)
    conversion_type VARCHAR(32) NOT NULL,
    -- Показы
    views INTEGER,
    -- Клики
    clicks INTEGER,
    -- CTR, %
    ctr NUMERIC(10, 4),
    -- Расходы
    sum DECIMAL(10, 2),
    -- Заказы
    orders INTEGER,
    -- CR, %
    cr NUMERIC(10, 4),
    -- CPC
    cpc NUMERIC(10, 4),
    -- CPA
    cpa DECIMAL(10, 2),
    -- Добавлено в корзину
    atbs INTEGER,
    -- Отмены
    canceled INTEGER,
    -- Заказано товаров, шт.
    shks INTEGER,
    -- Сумма заказов
    orders_sum DECIMAL(10, 2),
    -- Сумма заказов (sumPrice из API)
    sum_price DECIMAL(10, 2),
    -- Средняя позиция (только search + direct; иначе NULL)
    avg_pos NUMERIC(10, 2),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_wb_promo_stats_slices
        UNIQUE (campaign_id, nm_id, date, placement, conversion_type)
);

COMMENT ON TABLE solution.wb_promotion_campaign_statistics_slices IS
    'Статистика артикула в РК WB по дате с разрезами зоны показа и типа конверсии (v4 fullstats). placement/conversion_type = all — исторический агрегат из v3.';
COMMENT ON COLUMN solution.wb_promotion_campaign_statistics_slices.campaign_id IS 'ID кампании (advert_id)';
COMMENT ON COLUMN solution.wb_promotion_campaign_statistics_slices.nm_id IS 'Артикул WB (nmId)';
COMMENT ON COLUMN solution.wb_promotion_campaign_statistics_slices.date IS 'Дата статистики';
COMMENT ON COLUMN solution.wb_promotion_campaign_statistics_slices.placement IS 'Зона показов: search, recommendation или all';
COMMENT ON COLUMN solution.wb_promotion_campaign_statistics_slices.conversion_type IS 'Тип конверсии: direct, multicard, associated или all';
COMMENT ON COLUMN solution.wb_promotion_campaign_statistics_slices.views IS 'Показы';
COMMENT ON COLUMN solution.wb_promotion_campaign_statistics_slices.clicks IS 'Клики';
COMMENT ON COLUMN solution.wb_promotion_campaign_statistics_slices.ctr IS 'CTR, %';
COMMENT ON COLUMN solution.wb_promotion_campaign_statistics_slices.sum IS 'Расходы';
COMMENT ON COLUMN solution.wb_promotion_campaign_statistics_slices.orders IS 'Заказы';
COMMENT ON COLUMN solution.wb_promotion_campaign_statistics_slices.cr IS 'CR, %';
COMMENT ON COLUMN solution.wb_promotion_campaign_statistics_slices.cpc IS 'CPC';
COMMENT ON COLUMN solution.wb_promotion_campaign_statistics_slices.cpa IS 'CPA';
COMMENT ON COLUMN solution.wb_promotion_campaign_statistics_slices.atbs IS 'Добавлено в корзину';
COMMENT ON COLUMN solution.wb_promotion_campaign_statistics_slices.canceled IS 'Отмены';
COMMENT ON COLUMN solution.wb_promotion_campaign_statistics_slices.shks IS 'Заказано товаров, шт.';
COMMENT ON COLUMN solution.wb_promotion_campaign_statistics_slices.orders_sum IS 'Сумма заказов';
COMMENT ON COLUMN solution.wb_promotion_campaign_statistics_slices.sum_price IS 'Сумма заказов (sumPrice)';
COMMENT ON COLUMN solution.wb_promotion_campaign_statistics_slices.avg_pos IS 'Средняя позиция в поиске (search+direct)';
COMMENT ON COLUMN solution.wb_promotion_campaign_statistics_slices.created_at IS 'Создание записи';
COMMENT ON COLUMN solution.wb_promotion_campaign_statistics_slices.updated_at IS 'Обновление записи';

-- Перенос истории: одна строка v3 → одна строка-срез без разбивки
INSERT INTO solution.wb_promotion_campaign_statistics_slices (
    campaign_id,
    nm_id,
    date,
    placement,
    conversion_type,
    views,
    clicks,
    ctr,
    sum,
    orders,
    cr,
    cpc,
    cpa,
    atbs,
    canceled,
    shks,
    orders_sum,
    sum_price,
    avg_pos,
    created_at,
    updated_at
)
SELECT
    s.campaign_id,
    s.nm_id,
    s.date,
    'all',
    'all',
    s.views,
    s.clicks,
    s.ctr,
    s.sum,
    s.orders,
    s.cr,
    s.cpc,
    s.cpa,
    s.atbs,
    s.canceled,
    s.shks,
    s.orders_sum,
    s.sum_price,
    NULL,
    s.created_at,
    s.updated_at
FROM solution.wb_promotion_campaign_statistics s
ON CONFLICT (campaign_id, nm_id, date, placement, conversion_type) DO NOTHING;

COMMENT ON TABLE solution.wb_promotion_campaign_statistics IS
    'LEGACY: агрегат v3 fullstats. Актуальные данные — wb_promotion_campaign_statistics_slices. Таблицу не удалять до проверки миграции.';
