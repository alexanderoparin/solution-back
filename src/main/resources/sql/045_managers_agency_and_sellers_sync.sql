-- Текущие менеджеры считаем менеджерами агентства.
UPDATE solution.users
SET is_agency_client = true
WHERE role = 'MANAGER';

-- Селлеры: is_agency_client по флагу их менеджера-владельца.
UPDATE solution.users s
SET is_agency_client = m.is_agency_client
FROM solution.users m
WHERE s.role = 'SELLER'
  AND s.owner_id = m.id
  AND m.role = 'MANAGER'
  AND s.is_agency_client IS DISTINCT FROM m.is_agency_client;
