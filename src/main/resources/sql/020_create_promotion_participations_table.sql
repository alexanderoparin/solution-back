-- Таблица участия товаров кабинета в акциях календаря WB.
-- Заполняется при синхронизации календаря акций (по кабинету).
CREATE TABLE IF NOT EXISTS solution.promotion_participations (
    id BIGSERIAL PRIMARY KEY,
    cabinet_id BIGINT NOT NULL REFERENCES solution.cabinets(id) ON DELETE CASCADE,
    nm_id BIGINT NOT NULL,
    wb_promotion_id BIGINT NOT NULL,
    wb_promotion_name VARCHAR(500),
    CONSTRAINT uq_promotion_participation UNIQUE (cabinet_id, nm_id, wb_promotion_id)
);

CREATE INDEX IF NOT EXISTS idx_promotion_participations_cabinet_id
    ON solution.promotion_participations(cabinet_id);

COMMENT ON TABLE solution.promotion_participations IS 'Участие товаров (nmId) кабинета в акциях календаря WB';
COMMENT ON COLUMN solution.promotion_participations.cabinet_id IS 'Кабинет';
COMMENT ON COLUMN solution.promotion_participations.nm_id IS 'Артикул WB (nmId)';
COMMENT ON COLUMN solution.promotion_participations.wb_promotion_id IS 'ID акции в WB';
COMMENT ON COLUMN solution.promotion_participations.wb_promotion_name IS 'Название акции в WB';
