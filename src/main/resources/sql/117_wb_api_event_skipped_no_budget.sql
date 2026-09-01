-- Статус SKIPPED_NO_BUDGET: запуск РК не выполнен, потому что на WB нет бюджета.
-- Это терминальный бизнес-статус, не техническая ошибка очереди.
-- Авто-retry FAILED_FINAL (раз в 6 часов) такие события больше не подхватывает.

UPDATE solution.wb_api_events
SET status = 'SKIPPED_NO_BUDGET',
    updated_at = NOW()
WHERE status = 'FAILED_FINAL'
  AND event_type = 'PROMOTION_CAMPAIGN_START'
  AND last_error = 'нет бюджета для запуска';

COMMENT ON COLUMN solution.wb_api_events.status IS
    'Статус события: CREATED, RUNNING, SUCCESS, FAILED_RETRYABLE, FAILED_FINAL, FAILED_WITH_FALLBACK, DEFERRED_RATE_LIMIT, SKIPPED_NO_BUDGET, CANCELLED';
