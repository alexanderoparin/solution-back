ALTER TABLE solution.cabinets
ADD COLUMN IF NOT EXISTS last_stocks_update_at TIMESTAMP;
