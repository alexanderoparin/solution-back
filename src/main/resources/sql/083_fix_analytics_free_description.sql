-- Описание FREE как основного тарифа (не путать с пакетом А/Б).

UPDATE solution.plans
SET description = 'Разделы Товары, Сводная и Рекламные кампании. Дополнительные услуги подключаются отдельно.',
    updated_at = now()
WHERE code = 'analytics_free';

UPDATE solution.plans
SET name = 'Бесплатный доступ',
    updated_at = now()
WHERE code = 'analytics_free'
  AND (name IS DISTINCT FROM 'Бесплатный доступ');
