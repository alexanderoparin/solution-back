-- Создание таблицы promotion_campaign_statistics
-- Хранит статистику по каждому артикулу (nmId) в каждой кампании по датам
CREATE TABLE IF NOT EXISTS solution.promotion_campaign_statistics (
    id BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL REFERENCES solution.promotion_campaigns(advert_id) ON DELETE CASCADE,
    nm_id BIGINT NOT NULL,
    date DATE NOT NULL,
    views INTEGER,
    clicks INTEGER,
    ctr NUMERIC(10, 4),
    sum DECIMAL(10, 2),
    orders INTEGER,
    cr NUMERIC(10, 4),
    cpc NUMERIC(10, 4),
    cpa DECIMAL(10, 2),
    atbs INTEGER,
    canceled INTEGER,
    shks INTEGER,
    orders_sum DECIMAL(10, 2),
    sum_price DECIMAL(10, 2),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (campaign_id, nm_id, date)
);

-- Комментарии
COMMENT ON TABLE solution.promotion_campaign_statistics IS 'Таблица статистики артикулов внутри рекламных кампаний из WB API. Хранит статистику по каждому артикулу (nmId) в каждой кампании по датам.';
COMMENT ON COLUMN solution.promotion_campaign_statistics.id IS 'ID записи статистики';
COMMENT ON COLUMN solution.promotion_campaign_statistics.campaign_id IS 'ID кампании (advert_id)';
COMMENT ON COLUMN solution.promotion_campaign_statistics.nm_id IS 'Артикул товара (nmId)';
COMMENT ON COLUMN solution.promotion_campaign_statistics.date IS 'Дата статистики';
COMMENT ON COLUMN solution.promotion_campaign_statistics.views IS 'Показы';
COMMENT ON COLUMN solution.promotion_campaign_statistics.clicks IS 'Клики';
COMMENT ON COLUMN solution.promotion_campaign_statistics.ctr IS 'CTR (Click-Through Rate) - процент кликов от показов';
COMMENT ON COLUMN solution.promotion_campaign_statistics.sum IS 'Расходы (в рублях)';
COMMENT ON COLUMN solution.promotion_campaign_statistics.orders IS 'Заказы';
COMMENT ON COLUMN solution.promotion_campaign_statistics.cr IS 'CR (Conversion Rate) - процент заказов от кликов';
COMMENT ON COLUMN solution.promotion_campaign_statistics.cpc IS 'CPC (Cost Per Click) - стоимость клика';
COMMENT ON COLUMN solution.promotion_campaign_statistics.cpa IS 'CPA (Cost Per Action) - стоимость заказа (в рублях)';
COMMENT ON COLUMN solution.promotion_campaign_statistics.atbs IS 'Добавлено в корзину';
COMMENT ON COLUMN solution.promotion_campaign_statistics.canceled IS 'Отменено заказов';
COMMENT ON COLUMN solution.promotion_campaign_statistics.shks IS 'ШК (штрих-коды)';
COMMENT ON COLUMN solution.promotion_campaign_statistics.orders_sum IS 'Сумма заказов (в рублях)';
COMMENT ON COLUMN solution.promotion_campaign_statistics.sum_price IS 'Сумма заказов (в рублях) - альтернативное поле из API';
COMMENT ON COLUMN solution.promotion_campaign_statistics.created_at IS 'Дата создания записи в БД';
COMMENT ON COLUMN solution.promotion_campaign_statistics.updated_at IS 'Дата последнего обновления записи в БД';

