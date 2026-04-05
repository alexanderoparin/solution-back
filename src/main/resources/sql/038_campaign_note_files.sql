-- Файлы, прикреплённые к заметкам РК (аналог article_note_files).

CREATE TABLE IF NOT EXISTS solution.campaign_note_files (
    id BIGSERIAL PRIMARY KEY,
    note_id BIGINT NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    file_size BIGINT NOT NULL,
    mime_type VARCHAR(100),
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_campaign_note_files_note FOREIGN KEY (note_id) REFERENCES solution.campaign_notes(id) ON DELETE CASCADE
);

COMMENT ON TABLE solution.campaign_note_files IS 'Файлы заметок к рекламным кампаниям';
COMMENT ON COLUMN solution.campaign_note_files.id IS 'Уникальный идентификатор файла';
COMMENT ON COLUMN solution.campaign_note_files.note_id IS 'ID заметки (solution.campaign_notes)';
COMMENT ON COLUMN solution.campaign_note_files.file_name IS 'Оригинальное имя файла';
COMMENT ON COLUMN solution.campaign_note_files.file_path IS 'Путь к файлу на сервере';
COMMENT ON COLUMN solution.campaign_note_files.file_size IS 'Размер файла в байтах';
COMMENT ON COLUMN solution.campaign_note_files.mime_type IS 'MIME-тип файла';
COMMENT ON COLUMN solution.campaign_note_files.uploaded_at IS 'Дата и время загрузки файла';
