-- Выкупы из воронки WB (sales-funnel/products/history) — те же события синка, новые колонки.
ALTER TABLE solution.wb_product_card_analytics
    ADD COLUMN IF NOT EXISTS buyouts INTEGER,
    ADD COLUMN IF NOT EXISTS buyouts_sum NUMERIC(19, 2),
    ADD COLUMN IF NOT EXISTS buyout_percent NUMERIC(10, 4);

COMMENT ON COLUMN solution.wb_product_card_analytics.buyouts IS 'Выкупили товаров, шт. (WB buyoutCount)';
COMMENT ON COLUMN solution.wb_product_card_analytics.buyouts_sum IS 'Выкупили на сумму, руб. (WB buyoutSum)';
COMMENT ON COLUMN solution.wb_product_card_analytics.buyout_percent IS 'Процент выкупа, % (WB buyoutPercent)';
