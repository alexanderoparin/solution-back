-- Миграция: удаление поля is_bad_turnover
-- Дата: 2025-12-23
-- Описание: Удаляем неиспользуемое поле is_bad_turnover из таблицы product_price_history

-- Удаляем поле is_bad_turnover
ALTER TABLE solution.product_price_history 
DROP COLUMN IF EXISTS is_bad_turnover;

