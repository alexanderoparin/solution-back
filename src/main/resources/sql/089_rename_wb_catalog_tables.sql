-- Волна 1: префикс wb_ для каталога/аналитики товаров (WB-only домен).
-- Исторические миграции 001–088 не меняем.

ALTER TABLE IF EXISTS solution.product_cards RENAME TO wb_product_cards;
ALTER TABLE IF EXISTS solution.product_barcodes RENAME TO wb_product_barcodes;
ALTER TABLE IF EXISTS solution.product_card_analytics RENAME TO wb_product_card_analytics;
ALTER TABLE IF EXISTS solution.product_price_history RENAME TO wb_product_price_history;
ALTER TABLE IF EXISTS solution.article_notes RENAME TO wb_article_notes;
ALTER TABLE IF EXISTS solution.article_note_files RENAME TO wb_article_note_files;
ALTER TABLE IF EXISTS solution.article_goals RENAME TO wb_article_goals;

COMMENT ON TABLE solution.wb_product_cards IS 'Карточки товаров WB (nm_id)';
COMMENT ON TABLE solution.wb_product_barcodes IS 'Штрихкоды/размеры карточек WB';
COMMENT ON TABLE solution.wb_product_card_analytics IS 'Дневная воронка аналитики по карточкам WB';
COMMENT ON TABLE solution.wb_product_price_history IS 'История цен и СПП по карточкам WB';
COMMENT ON TABLE solution.wb_article_notes IS 'Заметки к артикулу WB (nm_id)';
COMMENT ON TABLE solution.wb_article_note_files IS 'Файлы заметок к артикулу WB';
COMMENT ON TABLE solution.wb_article_goals IS 'Цель на артикул WB (nm_id) в кабинете';
