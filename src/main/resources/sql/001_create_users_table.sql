-- Создание таблицы users
CREATE TABLE IF NOT EXISTS solution.users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'MANAGER', 'SELLER', 'WORKER')),
    owner_id BIGINT REFERENCES solution.users(id),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    password VARCHAR(255) NOT NULL,
    is_temporary_password BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Индексы
CREATE INDEX IF NOT EXISTS idx_users_email ON solution.users(email);
CREATE INDEX IF NOT EXISTS idx_users_role ON solution.users(role);
CREATE INDEX IF NOT EXISTS idx_users_owner_id ON solution.users(owner_id);
CREATE INDEX IF NOT EXISTS idx_users_is_temporary_password ON solution.users(is_temporary_password);

-- Комментарии
COMMENT ON TABLE solution.users IS 'Таблица пользователей системы';
COMMENT ON COLUMN solution.users.id IS 'Уникальный идентификатор пользователя';
COMMENT ON COLUMN solution.users.email IS 'Email пользователя (уникальный)';
COMMENT ON COLUMN solution.users.password IS 'Хеш пароля (BCrypt)';
COMMENT ON COLUMN solution.users.is_temporary_password IS 'Флаг временного пароля (требует смены при первом входе)';
COMMENT ON COLUMN solution.users.role IS 'Роль пользователя: ADMIN, MANAGER, SELLER, WORKER';
COMMENT ON COLUMN solution.users.owner_id IS 'ID владельца/родителя пользователя: для WORKER - SELLER, для SELLER - MANAGER, для MANAGER - ADMIN, для ADMIN - null';
COMMENT ON COLUMN solution.users.is_active IS 'Флаг активности пользователя';
COMMENT ON COLUMN solution.users.created_at IS 'Дата создания записи';
COMMENT ON COLUMN solution.users.updated_at IS 'Дата последнего обновления записи';

