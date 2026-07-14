-- Бессрочные подписки: expires_at NULL (например analytics_free).

ALTER TABLE solution.subscriptions
    ALTER COLUMN expires_at DROP NOT NULL;

COMMENT ON COLUMN solution.subscriptions.expires_at IS
    'Окончание периода; NULL — бессрочная подписка';

UPDATE solution.subscriptions s
SET expires_at = NULL,
    updated_at = NOW()
FROM solution.plans p
WHERE s.plan_id = p.id
  AND p.code = 'analytics_free';
