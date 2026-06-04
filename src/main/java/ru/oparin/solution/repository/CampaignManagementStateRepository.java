package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.CampaignManagementState;

import java.util.List;

@Repository
public interface CampaignManagementStateRepository extends JpaRepository<CampaignManagementState, Long> {

    List<CampaignManagementState> findByCabinetId(Long cabinetId);
}
