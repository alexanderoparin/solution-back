-- Миграция: создание таблиц для заметок к артикулам
-- Дата: 2026-01-07
-- Описание: Создаем таблицы article_notes и article_note_files для хранения заметок и файлов к артикулам

-- Таблица заметок к артикулам
CREATE TABLE IF NOT EXISTS solution.article_notes (
    id BIGSERIAL PRIMARY KEY,
    nm_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_article_notes_user FOREIGN KEY (user_id) REFERENCES solution.users(id) ON DELETE CASCADE,
    CONSTRAINT fk_article_notes_seller FOREIGN KEY (seller_id) REFERENCES solution.users(id) ON DELETE CASCADE
);

-- Комментарии к таблице и колонкам
COMMENT ON TABLE solution.article_notes IS 'Заметки к артикулам';
COMMENT ON COLUMN solution.article_notes.id IS 'Уникальный идентификатор заметки';
COMMENT ON COLUMN solution.article_notes.nm_id IS 'Артикул WB (nmId)';
COMMENT ON COLUMN solution.article_notes.seller_id IS 'ID продавца (SELLER), которому принадлежит артикул';
COMMENT ON COLUMN solution.article_notes.user_id IS 'ID пользователя, создавшего заметку';
COMMENT ON COLUMN solution.article_notes.content IS 'Текст заметки';
COMMENT ON COLUMN solution.article_notes.created_at IS 'Дата создания заметки';
COMMENT ON COLUMN solution.article_notes.updated_at IS 'Дата последнего обновления заметки';

-- Таблица файлов заметок
CREATE TABLE IF NOT EXISTS solution.article_note_files (
    id BIGSERIAL PRIMARY KEY,
    note_id BIGINT NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    file_size BIGINT NOT NULL,
    mime_type VARCHAR(100),
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_article_note_files_note FOREIGN KEY (note_id) REFERENCES solution.article_notes(id) ON DELETE CASCADE
);

-- Комментарии к таблице и колонкам
COMMENT ON TABLE solution.article_note_files IS 'Файлы, прикрепленные к заметкам';
COMMENT ON COLUMN solution.article_note_files.id IS 'Уникальный идентификатор файла';
COMMENT ON COLUMN solution.article_note_files.note_id IS 'ID заметки, к которой прикреплен файл';
COMMENT ON COLUMN solution.article_note_files.file_name IS 'Оригинальное имя файла';
COMMENT ON COLUMN solution.article_note_files.file_path IS 'Путь к файлу на сервере';
COMMENT ON COLUMN solution.article_note_files.file_size IS 'Размер файла в байтах';
COMMENT ON COLUMN solution.article_note_files.mime_type IS 'MIME-тип файла';
COMMENT ON COLUMN solution.article_note_files.uploaded_at IS 'Дата загрузки файла';

