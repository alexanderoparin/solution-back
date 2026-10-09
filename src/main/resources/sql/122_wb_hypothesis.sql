-- Гипотезы: проверка идей по артикулу на периоде с сравнением с таким же по длине «до».
CREATE TABLE IF NOT EXISTS solution.wb_hypothesis (
    id                  BIGSERIAL PRIMARY KEY,
    cabinet_id          BIGINT NOT NULL REFERENCES solution.cabinets(id) ON DELETE CASCADE,
    nm_id               BIGINT NOT NULL,
    title               VARCHAR(500) NOT NULL,
    description         TEXT,
    check_from          DATE NOT NULL,
    check_to            DATE NOT NULL,
    workflow_status     VARCHAR(32) NOT NULL,
    criteria_json       TEXT NOT NULL,
    verdict             VARCHAR(32),
    verdict_manual      BOOLEAN NOT NULL DEFAULT FALSE,
    created_by_user_id  BIGINT REFERENCES solution.users(id) ON DELETE SET NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_wb_hypothesis_dates CHECK (check_to >= check_from),
    CONSTRAINT chk_wb_hypothesis_workflow CHECK (workflow_status IN ('PREPARATION', 'TEST_LAUNCHED')),
    CONSTRAINT chk_wb_hypothesis_verdict CHECK (verdict IS NULL OR verdict IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX IF NOT EXISTS idx_wb_hypothesis_cabinet ON solution.wb_hypothesis(cabinet_id);
CREATE INDEX IF NOT EXISTS idx_wb_hypothesis_cabinet_nm ON solution.wb_hypothesis(cabinet_id, nm_id);
CREATE INDEX IF NOT EXISTS idx_wb_hypothesis_check_period ON solution.wb_hypothesis(cabinet_id, check_from, check_to);

COMMENT ON TABLE solution.wb_hypothesis IS 'Гипотезы продавца: сравнение метрик артикула за период проверки с равным периодом до него';
COMMENT ON COLUMN solution.wb_hypothesis.workflow_status IS 'Подготовка к тесту / Тест запущен (задаёт пользователь)';
COMMENT ON COLUMN solution.wb_hypothesis.criteria_json IS 'JSON-массив ключей критериев: drr, orders, ctr, cpc, cpo, views, buyout_percent, avg_pos';
COMMENT ON COLUMN solution.wb_hypothesis.verdict IS 'Итог Успешно/Неуспешно; NULL пока нет решения';
COMMENT ON COLUMN solution.wb_hypothesis.verdict_manual IS 'true — итог задан вручную и не пересчитывается автоматически';
