-- Время запроса обновления (нажатие кнопки). Сбрасывается при реальном старте задачи.
-- Нужно для блокировки кнопки «Обновить данные», пока задача в очереди.
ALTER TABLE solution.cabinets
ADD COLUMN IF NOT EXISTS last_data_update_requested_at TIMESTAMP;
