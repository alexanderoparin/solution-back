-- Тарифные планы подписки: Базовый (месяц), Выгодный (год).
INSERT INTO solution.plans (name, description, price_rub, period_days, max_cabinets, sort_order, is_active)
SELECT 'Базовый', 'Доступ на 1 месяц', 2999.00, 30, NULL, 1, true
WHERE NOT EXISTS (SELECT 1 FROM solution.plans WHERE name = 'Базовый');

INSERT INTO solution.plans (name, description, price_rub, period_days, max_cabinets, sort_order, is_active)
SELECT 'Выгодный', 'Доступ на 1 год', 29999.00, 365, NULL, 2, true
WHERE NOT EXISTS (SELECT 1 FROM solution.plans WHERE name = 'Выгодный');
