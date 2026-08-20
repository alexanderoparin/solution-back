package ru.oparin.solution.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.WbArticleNoteFile;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с файлами заметок.
 */
@Repository
public interface WbArticleNoteFileRepository extends JpaRepository<WbArticleNoteFile, Long> {

    /**
     * Находит все файлы для указанной заметки.
     *
     * @param noteId ID заметки
     * @return список файлов
     */
    List<WbArticleNoteFile> findByNoteIdOrderByUploadedAtAsc(Long noteId);

    /**
     * Находит файл по ID и ID заметки.
     *
     * @param id ID файла
     * @param noteId ID заметки
     * @return файл, если найден
     */
    @Query("SELECT f FROM WbArticleNoteFile f WHERE f.id = :id AND f.note.id = :noteId")
    Optional<WbArticleNoteFile> findByIdAndNoteId(@Param("id") Long id, @Param("noteId") Long noteId);

    /**
     * Проверяет существование файла по ID и ID заметки.
     *
     * @param id ID файла
     * @param noteId ID заметки
     * @return true, если файл существует
     */
    @Query("SELECT COUNT(f) > 0 FROM WbArticleNoteFile f WHERE f.id = :id AND f.note.id = :noteId")
    boolean existsByIdAndNoteId(@Param("id") Long id, @Param("noteId") Long noteId);

    /**
     * Возвращает пути ко всем файлам заметок по артикулам.
     */
    @Query("SELECT f.filePath FROM WbArticleNoteFile f")
    List<String> findAllFilePaths();

    /**
     * Находит файлы заметок по артикулам для указанного кабинета.
     */
    Page<WbArticleNoteFile> findByNote_CabinetId(Long cabinetId, Pageable pageable);
}

