-- Создание таблицы product_cards
-- Хранение информации о карточках товаров из WB API
CREATE TABLE IF NOT EXISTS solution.product_cards (
    nm_id BIGINT PRIMARY KEY,
    imt_id BIGINT,
    seller_id BIGINT NOT NULL REFERENCES solution.users(id) ON DELETE CASCADE,
    title VARCHAR(500),
    subject_name VARCHAR(255),
    brand VARCHAR(255),
    vendor_code VARCHAR(255),
    photo_tm VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Комментарии
COMMENT ON TABLE solution.product_cards IS 'Таблица для хранения информации о карточках товаров из WB API';
COMMENT ON COLUMN solution.product_cards.nm_id IS 'Уникальный идентификатор карточки товара (nmID из WB API). Первичный ключ';
COMMENT ON COLUMN solution.product_cards.imt_id IS 'ID карточки товара (imtID из WB API). Карточки с одинаковым imtID считаются объединёнными';
COMMENT ON COLUMN solution.product_cards.seller_id IS 'ID продавца (SELLER), владельца карточки';
COMMENT ON COLUMN solution.product_cards.title IS 'Название товара';
COMMENT ON COLUMN solution.product_cards.subject_name IS 'Название категории товара';
COMMENT ON COLUMN solution.product_cards.brand IS 'Бренд товара';
COMMENT ON COLUMN solution.product_cards.vendor_code IS 'Артикул продавца';
COMMENT ON COLUMN solution.product_cards.photo_tm IS 'URL миниатюры первой фотографии товара';

