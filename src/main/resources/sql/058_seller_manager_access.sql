-- Делегирование доступа менеджера к аккаунту селлера (many-to-many).

CREATE TABLE IF NOT EXISTS solution.seller_manager_access (
    id BIGSERIAL PRIMARY KEY,
    seller_id BIGINT NOT NULL REFERENCES solution.users(id) ON DELETE CASCADE,
    manager_id BIGINT NOT NULL REFERENCES solution.users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    granted_at TIMESTAMP NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMP,
    CONSTRAINT uq_seller_manager_access UNIQUE (seller_id, manager_id)
);

COMMENT ON TABLE solution.seller_manager_access IS
    'Делегирование селлером доступа менеджеру ко всем кабинетам селлера.';
COMMENT ON COLUMN solution.seller_manager_access.seller_id IS 'Селлер, выдавший доступ.';
COMMENT ON COLUMN solution.seller_manager_access.manager_id IS 'Менеджер с доступом.';
COMMENT ON COLUMN solution.seller_manager_access.status IS 'ACTIVE или REVOKED.';
COMMENT ON COLUMN solution.seller_manager_access.granted_at IS 'Когда доступ выдан или восстановлен.';
COMMENT ON COLUMN solution.seller_manager_access.revoked_at IS 'Когда доступ отозван (если status=REVOKED).';

CREATE INDEX IF NOT EXISTS idx_seller_manager_access_manager_status
    ON solution.seller_manager_access (manager_id, status);

CREATE INDEX IF NOT EXISTS idx_seller_manager_access_seller_status
    ON solution.seller_manager_access (seller_id, status);

-- Миграция существующих связей seller.owner_id -> manager.
INSERT INTO solution.seller_manager_access (seller_id, manager_id, status, granted_at)
SELECT s.id, s.owner_id, 'ACTIVE', COALESCE(s.created_at, NOW())
FROM solution.users s
JOIN solution.users m ON m.id = s.owner_id AND m.role = 'MANAGER'
WHERE s.role = 'SELLER'
  AND s.owner_id IS NOT NULL
ON CONFLICT (seller_id, manager_id) DO NOTHING;
