-- Время последнего запуска обновления остатков по кабинету (нажатие кнопки «Обновить остатки»).
-- Используется для ограничения «не чаще раза в час» и для отображения на фронте.
ALTER TABLE solution.cabinets
ADD COLUMN IF NOT EXISTS last_stocks_update_requested_at TIMESTAMP;
