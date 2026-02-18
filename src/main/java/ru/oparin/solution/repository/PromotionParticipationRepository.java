package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.PromotionParticipation;

import java.util.List;

@Repository
public interface PromotionParticipationRepository extends JpaRepository<PromotionParticipation, Long> {

    void deleteByCabinet_Id(Long cabinetId);

    List<PromotionParticipation> findByCabinet_Id(Long cabinetId);

    List<PromotionParticipation> findByCabinet_IdAndNmId(Long cabinetId, Long nmId);
}
