-- Удаление legacy-таблиц делегирования доступа.
-- Доступ уже в solution.cabinet_access_grants (см. 068); код на grants.

DROP TABLE IF EXISTS solution.seller_manager_access;
DROP TABLE IF EXISTS solution.seller_worker;
