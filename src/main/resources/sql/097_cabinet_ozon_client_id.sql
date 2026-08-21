-- Client-Id Seller API для кабинетов Ozon (Api-Key хранится в api_key).

ALTER TABLE solution.cabinets
    ADD COLUMN IF NOT EXISTS ozon_client_id VARCHAR(64);

COMMENT ON COLUMN solution.cabinets.ozon_client_id IS 'Ozon Seller API Client-Id (только для marketplace_type=OZON)';
