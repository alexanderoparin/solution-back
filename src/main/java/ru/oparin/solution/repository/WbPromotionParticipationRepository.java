package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.WbPromotionParticipation;

import java.util.List;

@Repository
public interface WbPromotionParticipationRepository extends JpaRepository<WbPromotionParticipation, Long> {

    void deleteByCabinet_Id(Long cabinetId);

    List<WbPromotionParticipation> findByCabinet_Id(Long cabinetId);

    List<WbPromotionParticipation> findByCabinet_IdAndNmId(Long cabinetId, Long nmId);
}
