-- Создание таблицы promotion_campaigns
CREATE TABLE IF NOT EXISTS solution.promotion_campaigns (
    advert_id BIGINT PRIMARY KEY,
    seller_id BIGINT NOT NULL REFERENCES solution.users(id) ON DELETE CASCADE,
    name VARCHAR(500) NOT NULL,
    type INTEGER NOT NULL CHECK (type IN (4, 5, 6, 7, 8, 9)),
    status INTEGER NOT NULL,
    bid_type INTEGER CHECK (bid_type IN (1, 2)),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    create_time TIMESTAMP,
    change_time TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Комментарии
COMMENT ON TABLE solution.promotion_campaigns IS 'Таблица рекламных кампаний из WB API';
COMMENT ON COLUMN solution.promotion_campaigns.advert_id IS 'ID кампании из WB API (advertId)';
COMMENT ON COLUMN solution.promotion_campaigns.seller_id IS 'ID продавца, владельца кампании';
COMMENT ON COLUMN solution.promotion_campaigns.name IS 'Название кампании';
COMMENT ON COLUMN solution.promotion_campaigns.type IS 'Числовой код типа кампании из WB API (4, 5, 6, 7, 8, 9). Конвертируется в enum CampaignType через AttributeConverter';
COMMENT ON COLUMN solution.promotion_campaigns.status IS 'Числовой код статуса кампании из WB API (4, 7, 9, 11). Конвертируется в enum CampaignStatus через AttributeConverter';
COMMENT ON COLUMN solution.promotion_campaigns.bid_type IS 'Числовой код типа ставки из WB API (1 - manual, 2 - unified). Конвертируется в enum BidType через AttributeConverter';
COMMENT ON COLUMN solution.promotion_campaigns.start_time IS 'Дата начала кампании';
COMMENT ON COLUMN solution.promotion_campaigns.end_time IS 'Дата окончания кампании';
COMMENT ON COLUMN solution.promotion_campaigns.create_time IS 'Дата создания кампании в WB';
COMMENT ON COLUMN solution.promotion_campaigns.change_time IS 'Дата последнего изменения кампании в WB';
COMMENT ON COLUMN solution.promotion_campaigns.created_at IS 'Дата создания записи в БД';
COMMENT ON COLUMN solution.promotion_campaigns.updated_at IS 'Дата последнего обновления записи в БД';

