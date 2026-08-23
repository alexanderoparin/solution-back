-- Ozon Performance API: учётные данные в кабинете и список рекламных кампаний (Wave 3).

ALTER TABLE solution.cabinets
    ADD COLUMN IF NOT EXISTS ozon_performance_client_id VARCHAR(128);

ALTER TABLE solution.cabinets
    ADD COLUMN IF NOT EXISTS ozon_performance_client_secret VARCHAR(500);

ALTER TABLE solution.cabinets
    ADD COLUMN IF NOT EXISTS ozon_performance_is_valid BOOLEAN;

ALTER TABLE solution.cabinets
    ADD COLUMN IF NOT EXISTS ozon_performance_last_validated_at TIMESTAMP;

ALTER TABLE solution.cabinets
    ADD COLUMN IF NOT EXISTS ozon_performance_validation_error TEXT;

ALTER TABLE solution.cabinets
    ADD COLUMN IF NOT EXISTS last_ozon_campaigns_sync_at TIMESTAMP;

COMMENT ON COLUMN solution.cabinets.ozon_performance_client_id IS 'Ozon Performance API client_id (отдельно от Seller Client-Id)';
COMMENT ON COLUMN solution.cabinets.ozon_performance_client_secret IS 'Ozon Performance API client_secret';
COMMENT ON COLUMN solution.cabinets.ozon_performance_is_valid IS 'Результат последней проверки Performance credentials (null — не проверяли)';
COMMENT ON COLUMN solution.cabinets.ozon_performance_last_validated_at IS 'Время последней проверки Performance credentials';
COMMENT ON COLUMN solution.cabinets.ozon_performance_validation_error IS 'Текст ошибки проверки Performance credentials';
COMMENT ON COLUMN solution.cabinets.last_ozon_campaigns_sync_at IS 'Время последней успешной синхронизации списка РК Ozon';

CREATE TABLE IF NOT EXISTS solution.ozon_promotion_campaigns (
    campaign_id BIGINT PRIMARY KEY,
    cabinet_id BIGINT NOT NULL REFERENCES solution.cabinets(id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    state VARCHAR(64) NOT NULL,
    adv_object_type VARCHAR(32),
    payment_type VARCHAR(16),
    daily_budget BIGINT,
    budget BIGINT,
    from_date DATE,
    to_date DATE,
    ozon_created_at TIMESTAMP,
    ozon_updated_at TIMESTAMP,
    synced_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE solution.ozon_promotion_campaigns IS 'Рекламные кампании Ozon Performance API (список /api/client/campaign).';
COMMENT ON COLUMN solution.ozon_promotion_campaigns.campaign_id IS 'ID кампании в Ozon Performance API';
COMMENT ON COLUMN solution.ozon_promotion_campaigns.cabinet_id IS 'Кабинет продавца';
COMMENT ON COLUMN solution.ozon_promotion_campaigns.title IS 'Название кампании';
COMMENT ON COLUMN solution.ozon_promotion_campaigns.state IS 'Состояние кампании (CAMPAIGN_STATE_* из API)';
COMMENT ON COLUMN solution.ozon_promotion_campaigns.adv_object_type IS 'Тип объекта рекламы: SKU, BANNER, SEARCH_PROMO, VIDEO_BANNER';
COMMENT ON COLUMN solution.ozon_promotion_campaigns.payment_type IS 'Модель оплаты: CPC, CPM, CPO и т.д.';
COMMENT ON COLUMN solution.ozon_promotion_campaigns.daily_budget IS 'Дневной бюджет (единицы API, микрокопейки)';
COMMENT ON COLUMN solution.ozon_promotion_campaigns.budget IS 'Общий бюджет (единицы API)';
COMMENT ON COLUMN solution.ozon_promotion_campaigns.from_date IS 'Дата начала кампании';
COMMENT ON COLUMN solution.ozon_promotion_campaigns.to_date IS 'Дата окончания кампании';
COMMENT ON COLUMN solution.ozon_promotion_campaigns.ozon_created_at IS 'createdAt из Ozon API';
COMMENT ON COLUMN solution.ozon_promotion_campaigns.ozon_updated_at IS 'updatedAt из Ozon API';
COMMENT ON COLUMN solution.ozon_promotion_campaigns.synced_at IS 'Время последней синхронизации записи';

CREATE INDEX IF NOT EXISTS idx_ozon_promotion_campaigns_cabinet
    ON solution.ozon_promotion_campaigns (cabinet_id);
