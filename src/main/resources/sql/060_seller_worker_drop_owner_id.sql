-- Привязка работника (WORKER) к селлеру; замена users.owner_id.

CREATE TABLE IF NOT EXISTS solution.seller_worker (
    id BIGSERIAL PRIMARY KEY,
    seller_id BIGINT NOT NULL REFERENCES solution.users(id) ON DELETE CASCADE,
    worker_id BIGINT NOT NULL REFERENCES solution.users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_seller_worker_worker UNIQUE (worker_id)
);

COMMENT ON TABLE solution.seller_worker IS 'Селлер — работник: один работник принадлежит одному селлеру.';
COMMENT ON COLUMN solution.seller_worker.seller_id IS 'Селлер-работодатель.';
COMMENT ON COLUMN solution.seller_worker.worker_id IS 'Учётная запись WORKER.';
COMMENT ON COLUMN solution.seller_worker.created_at IS 'Когда работник привязан к селлеру.';

CREATE INDEX IF NOT EXISTS idx_seller_worker_seller_id ON solution.seller_worker (seller_id);

INSERT INTO solution.seller_worker (seller_id, worker_id, created_at)
SELECT u.owner_id, u.id, COALESCE(u.created_at, NOW())
FROM solution.users u
WHERE u.role = 'WORKER'
  AND u.owner_id IS NOT NULL
ON CONFLICT (worker_id) DO NOTHING;

ALTER TABLE solution.users DROP CONSTRAINT IF EXISTS users_owner_id_fkey;
DROP INDEX IF EXISTS solution.idx_users_owner_id;
ALTER TABLE solution.users DROP COLUMN IF EXISTS owner_id;
