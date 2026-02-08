-- Миграция: удаление поля spp_price и добавление поля spp_discount
-- Дата: 2025-12-23
-- Описание: Заменяем поле spp_price (цена с СПП в рублях) на spp_discount (скидка СПП в процентах)

-- Удаляем старое поле spp_price
ALTER TABLE solution.product_price_history 
DROP COLUMN IF EXISTS spp_price;

-- Добавляем новое поле spp_discount (скидка СПП в процентах)
ALTER TABLE solution.product_price_history 
ADD COLUMN spp_discount INTEGER;

-- Комментарий к колонке
COMMENT ON COLUMN solution.product_price_history.spp_discount IS 'Скидка СПП (Скидка постоянного покупателя) в процентах. СПП - это скидка, которую дает сам Wildberries постоянным покупателям.';

