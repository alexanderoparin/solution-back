package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.ArticleNoteFile;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с файлами заметок.
 */
@Repository
public interface ArticleNoteFileRepository extends JpaRepository<ArticleNoteFile, Long> {

    /**
     * Находит все файлы для указанной заметки.
     *
     * @param noteId ID заметки
     * @return список файлов
     */
    List<ArticleNoteFile> findByNoteIdOrderByUploadedAtAsc(Long noteId);

    /**
     * Находит файл по ID и ID заметки.
     *
     * @param id ID файла
     * @param noteId ID заметки
     * @return файл, если найден
     */
    @Query("SELECT f FROM ArticleNoteFile f WHERE f.id = :id AND f.note.id = :noteId")
    Optional<ArticleNoteFile> findByIdAndNoteId(@Param("id") Long id, @Param("noteId") Long noteId);

    /**
     * Проверяет существование файла по ID и ID заметки.
     *
     * @param id ID файла
     * @param noteId ID заметки
     * @return true, если файл существует
     */
    @Query("SELECT COUNT(f) > 0 FROM ArticleNoteFile f WHERE f.id = :id AND f.note.id = :noteId")
    boolean existsByIdAndNoteId(@Param("id") Long id, @Param("noteId") Long noteId);
}

