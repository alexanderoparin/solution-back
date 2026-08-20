-- Волна 4: префикс wb_ для A/B тестов фото (WB media).

ALTER TABLE IF EXISTS solution.ab_test RENAME TO wb_ab_test;
ALTER TABLE IF EXISTS solution.ab_test_campaign RENAME TO wb_ab_test_campaign;
ALTER TABLE IF EXISTS solution.ab_test_variant RENAME TO wb_ab_test_variant;
ALTER TABLE IF EXISTS solution.ab_test_rotation_log RENAME TO wb_ab_test_rotation_log;
ALTER TABLE IF EXISTS solution.ab_test_stats_snapshot RENAME TO wb_ab_test_stats_snapshot;
ALTER TABLE IF EXISTS solution.cabinet_ab_test_quota RENAME TO wb_cabinet_ab_test_quota;

COMMENT ON TABLE solution.wb_ab_test IS 'A/B тест фото карточки WB';
COMMENT ON TABLE solution.wb_ab_test_campaign IS 'Связь A/B теста с РК WB';
COMMENT ON TABLE solution.wb_ab_test_variant IS 'Вариант A/B теста WB';
COMMENT ON TABLE solution.wb_ab_test_rotation_log IS 'Лог ротаций A/B теста WB';
COMMENT ON TABLE solution.wb_ab_test_stats_snapshot IS 'Снимки статистики A/B теста WB';
COMMENT ON TABLE solution.wb_cabinet_ab_test_quota IS 'Квота A/B тестов кабинета (WB)';
