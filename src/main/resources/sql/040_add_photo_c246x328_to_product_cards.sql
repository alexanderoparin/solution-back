-- URL превью 246×328 (WB photos[].c246x328); для шапки артикула. tm (75×100) по-прежнему в photo_tm.
-- Выполнять после миграций схемы solution.

ALTER TABLE solution.product_cards
    ADD COLUMN IF NOT EXISTS photo_c246x328 VARCHAR(1000);

COMMENT ON COLUMN solution.product_cards.photo_c246x328 IS 'URL первой фотографии в размере c246x328 из WB API';
COMMENT ON COLUMN solution.product_cards.photo_tm IS 'URL первой фотографии в размере c75x100 из WB API';
