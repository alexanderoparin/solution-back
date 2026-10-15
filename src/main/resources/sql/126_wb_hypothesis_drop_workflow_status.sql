-- Удаление избыточного ручного статуса гипотезы (Подготовка / Тест запущен).
-- Отображаемый статус считается автоматически по датам периода и результатам.

ALTER TABLE solution.wb_hypothesis
    DROP CONSTRAINT IF EXISTS chk_wb_hypothesis_workflow;

ALTER TABLE solution.wb_hypothesis
    DROP COLUMN IF EXISTS workflow_status;
