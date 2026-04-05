package ru.oparin.solution.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.CampaignNoteFile;

import java.util.List;
import java.util.Optional;

@Repository
public interface CampaignNoteFileRepository extends JpaRepository<CampaignNoteFile, Long> {

    List<CampaignNoteFile> findByNote_IdOrderByUploadedAtAsc(Long noteId);

    Page<CampaignNoteFile> findByNote_CabinetId(Long cabinetId, Pageable pageable);

    @Query("SELECT f FROM CampaignNoteFile f WHERE f.id = :id AND f.note.id = :noteId")
    Optional<CampaignNoteFile> findByIdAndNoteId(@Param("id") Long id, @Param("noteId") Long noteId);
}
