-- Удаление legacy-таблиц доступа менеджеров и работников после миграции на cabinet_access_grants (068).

DROP TABLE IF EXISTS seller_manager_access;
DROP TABLE IF EXISTS seller_worker;
