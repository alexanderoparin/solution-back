-- Волна 2: префикс wb_ для остатков FBO/FBS (WB-only).
-- wb_warehouses уже с префиксом — не трогаем.

ALTER TABLE IF EXISTS solution.product_stocks RENAME TO wb_product_stocks;
ALTER TABLE IF EXISTS solution.seller_warehouses RENAME TO wb_seller_warehouses;
ALTER TABLE IF EXISTS solution.product_fbs_stocks RENAME TO wb_product_fbs_stocks;

COMMENT ON TABLE solution.wb_product_stocks IS 'Остатки FBO WB по складам';
COMMENT ON TABLE solution.wb_seller_warehouses IS 'Склады продавца FBS (WB Marketplace API)';
COMMENT ON TABLE solution.wb_product_fbs_stocks IS 'Остатки FBS WB по складам продавца';
