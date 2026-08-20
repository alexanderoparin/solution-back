package ru.oparin.solution.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.WbCampaignNoteFile;

import java.util.List;
import java.util.Optional;

@Repository
public interface WbCampaignNoteFileRepository extends JpaRepository<WbCampaignNoteFile, Long> {

    List<WbCampaignNoteFile> findByNote_IdOrderByUploadedAtAsc(Long noteId);

    Page<WbCampaignNoteFile> findByNote_CabinetId(Long cabinetId, Pageable pageable);

    @Query("SELECT f FROM WbCampaignNoteFile f WHERE f.id = :id AND f.note.id = :noteId")
    Optional<WbCampaignNoteFile> findByIdAndNoteId(@Param("id") Long id, @Param("noteId") Long noteId);

    /**
     * Возвращает пути ко всем файлам заметок РК.
     */
    @Query("SELECT f.filePath FROM WbCampaignNoteFile f")
    List<String> findAllFilePaths();
}
