-- Модель оплаты рекламной кампании из settings.payment_type WB API.
-- Нужна для разделения ручной ставки CPM и кампаний с оплатой за клики CPC.

ALTER TABLE solution.promotion_campaigns
    ADD COLUMN IF NOT EXISTS payment_type VARCHAR(10);

COMMENT ON COLUMN solution.promotion_campaigns.payment_type IS
    'Модель оплаты WB: CPM — за показы, CPC — за клики';
