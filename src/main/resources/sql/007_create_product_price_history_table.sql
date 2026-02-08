-- Создание таблицы product_price_history
CREATE TABLE IF NOT EXISTS solution.product_price_history (
    id BIGSERIAL PRIMARY KEY,
    nm_id BIGINT NOT NULL,
    date DATE NOT NULL,
    size_id BIGINT,
    tech_size_name VARCHAR(50),
    price DECIMAL(10, 2) NOT NULL,
    discounted_price DECIMAL(10, 2) NOT NULL,
    club_discounted_price DECIMAL(10, 2) NOT NULL,
    spp_price DECIMAL(10, 2),
    discount INTEGER NOT NULL,
    club_discount INTEGER NOT NULL,
    editable_size_price BOOLEAN,
    is_bad_turnover BOOLEAN,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (nm_id, date, size_id),
    
    -- Внешний ключ
    CONSTRAINT fk_product_price_history_nm_id FOREIGN KEY (nm_id) REFERENCES solution.product_cards(nm_id) ON DELETE CASCADE
);

-- Комментарии
COMMENT ON TABLE solution.product_price_history IS 'История изменения цен товаров по датам';
COMMENT ON COLUMN solution.product_price_history.id IS 'ID записи';
COMMENT ON COLUMN solution.product_price_history.nm_id IS 'Артикул WB (nmID)';
COMMENT ON COLUMN solution.product_price_history.date IS 'Дата, за которую сохранена цена (вчерашняя дата)';
COMMENT ON COLUMN solution.product_price_history.size_id IS 'ID размера (null если цена одинаковая для всех размеров)';
COMMENT ON COLUMN solution.product_price_history.tech_size_name IS 'Название размера (42, M, L и т.д.)';
COMMENT ON COLUMN solution.product_price_history.price IS 'Цена до скидки (в рублях)';
COMMENT ON COLUMN solution.product_price_history.discounted_price IS 'Цена со скидкой продавца (в рублях)';
COMMENT ON COLUMN solution.product_price_history.club_discounted_price IS 'Цена со скидкой WB Клуба (в рублях)';
COMMENT ON COLUMN solution.product_price_history.spp_price IS 'Цена с СПП (Скидка постоянного покупателя) в рублях. СПП - это скидка, которую дает сам Wildberries постоянным покупателям.';
COMMENT ON COLUMN solution.product_price_history.discount IS 'Скидка продавца (%)';
COMMENT ON COLUMN solution.product_price_history.club_discount IS 'Скидка WB Клуба (%)';
COMMENT ON COLUMN solution.product_price_history.editable_size_price IS 'Можно ли редактировать цену размера';
COMMENT ON COLUMN solution.product_price_history.is_bad_turnover IS 'Плохой оборот товара';
COMMENT ON COLUMN solution.product_price_history.created_at IS 'Дата создания записи в БД';

