-- Создание таблицы users
CREATE TABLE IF NOT EXISTS solution.users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'SELLER', 'WORKER')),
    seller_id BIGINT REFERENCES solution.users(id),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Индексы
CREATE INDEX IF NOT EXISTS idx_users_email ON solution.users(email);
CREATE INDEX IF NOT EXISTS idx_users_role ON solution.users(role);
CREATE INDEX IF NOT EXISTS idx_users_seller_id ON solution.users(seller_id);

-- Комментарии
COMMENT ON TABLE solution.users IS 'Таблица пользователей системы';
COMMENT ON COLUMN solution.users.id IS 'Уникальный идентификатор пользователя';
COMMENT ON COLUMN solution.users.email IS 'Email пользователя (уникальный)';
COMMENT ON COLUMN solution.users.password IS 'Хеш пароля (BCrypt)';
COMMENT ON COLUMN solution.users.role IS 'Роль пользователя: ADMIN, SELLER, WORKER';
COMMENT ON COLUMN solution.users.seller_id IS 'ID селлера для WORKER (null для ADMIN и SELLER)';
COMMENT ON COLUMN solution.users.is_active IS 'Флаг активности пользователя';
COMMENT ON COLUMN solution.users.created_at IS 'Дата создания записи';
COMMENT ON COLUMN solution.users.updated_at IS 'Дата последнего обновления записи';

