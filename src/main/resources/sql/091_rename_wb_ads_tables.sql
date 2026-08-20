-- Волна 3: префикс wb_ для рекламы WB.

ALTER TABLE IF EXISTS solution.promotion_campaigns RENAME TO wb_promotion_campaigns;
ALTER TABLE IF EXISTS solution.promotion_campaign_statistics RENAME TO wb_promotion_campaign_statistics;
ALTER TABLE IF EXISTS solution.promotion_norm_query_statistics RENAME TO wb_promotion_norm_query_statistics;
ALTER TABLE IF EXISTS solution.promotion_participations RENAME TO wb_promotion_participations;
ALTER TABLE IF EXISTS solution.campaign_articles RENAME TO wb_campaign_articles;
ALTER TABLE IF EXISTS solution.campaign_notes RENAME TO wb_campaign_notes;
ALTER TABLE IF EXISTS solution.campaign_note_files RENAME TO wb_campaign_note_files;
ALTER TABLE IF EXISTS solution.campaign_goals RENAME TO wb_campaign_goals;
ALTER TABLE IF EXISTS solution.campaign_schedule_slot RENAME TO wb_campaign_schedule_slot;
ALTER TABLE IF EXISTS solution.campaign_auto_budget_settings RENAME TO wb_campaign_auto_budget_settings;
ALTER TABLE IF EXISTS solution.campaign_management_state RENAME TO wb_campaign_management_state;
ALTER TABLE IF EXISTS solution.campaign_change_log RENAME TO wb_campaign_change_log;
ALTER TABLE IF EXISTS solution.campaign_budget_timeline RENAME TO wb_campaign_budget_timeline;
ALTER TABLE IF EXISTS solution.cabinet_promotion_balance_cache RENAME TO wb_cabinet_promotion_balance_cache;

COMMENT ON TABLE solution.wb_promotion_campaigns IS 'Рекламные кампании WB';
COMMENT ON TABLE solution.wb_promotion_campaign_statistics IS 'Статистика РК WB';
COMMENT ON TABLE solution.wb_promotion_norm_query_statistics IS 'Статистика нормзапросов РК WB';
COMMENT ON TABLE solution.wb_promotion_participations IS 'Участие артикулов в акциях WB';
COMMENT ON TABLE solution.wb_campaign_articles IS 'Связь РК WB с артикулами (nm_id)';
COMMENT ON TABLE solution.wb_campaign_notes IS 'Заметки к РК WB';
COMMENT ON TABLE solution.wb_campaign_note_files IS 'Файлы заметок к РК WB';
COMMENT ON TABLE solution.wb_campaign_goals IS 'Цели РК WB';
COMMENT ON TABLE solution.wb_campaign_schedule_slot IS 'Слоты расписания биддера WB';
COMMENT ON TABLE solution.wb_campaign_auto_budget_settings IS 'Автопополнение бюджета РК WB';
COMMENT ON TABLE solution.wb_campaign_management_state IS 'Состояние управления РК WB';
COMMENT ON TABLE solution.wb_campaign_change_log IS 'Журнал изменений РК WB';
COMMENT ON TABLE solution.wb_campaign_budget_timeline IS 'Таймлайн бюджета РК WB';
COMMENT ON TABLE solution.wb_cabinet_promotion_balance_cache IS 'Кэш баланса продвижения кабинета WB';
