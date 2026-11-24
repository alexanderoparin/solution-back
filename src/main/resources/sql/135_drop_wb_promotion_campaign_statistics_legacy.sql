-- Удаление legacy-таблицы статистики РК WB (агрегат GET /adv/v3/fullstats).
-- Данные уже перенесены в wb_promotion_campaign_statistics_slices (см. 134_…).
-- Актуальный источник: GET /api/advert/v4/fullstats → …_statistics_slices.

DROP TABLE IF EXISTS solution.wb_promotion_campaign_statistics;
