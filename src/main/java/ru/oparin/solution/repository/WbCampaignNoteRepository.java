package ru.oparin.solution.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.WbCampaignNote;

import java.util.List;
import java.util.Optional;

@Repository
public interface WbCampaignNoteRepository extends JpaRepository<WbCampaignNote, Long> {

    List<WbCampaignNote> findByCampaignIdAndCabinetIdOrderByCreatedAtDesc(Long campaignId, Long cabinetId);

    Optional<WbCampaignNote> findByIdAndCampaignIdAndCabinetId(Long id, Long campaignId, Long cabinetId);

    boolean existsByIdAndCampaignIdAndCabinetId(Long id, Long campaignId, Long cabinetId);

    void deleteByCabinetId(Long cabinetId);

    @Query("SELECT n.id FROM WbCampaignNote n WHERE n.cabinetId = :cabinetId")
    List<Long> findIdByCabinetId(@Param("cabinetId") Long cabinetId, Pageable pageable);
}
