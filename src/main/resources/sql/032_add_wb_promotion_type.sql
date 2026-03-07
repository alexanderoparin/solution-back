-- Тип акции WB (regular, auto) для отображения в интерфейсе.
ALTER TABLE solution.promotion_participations
    ADD COLUMN IF NOT EXISTS wb_promotion_type VARCHAR(50);

COMMENT ON COLUMN solution.promotion_participations.wb_promotion_type IS 'Тип акции в WB: regular, auto и т.д.';
