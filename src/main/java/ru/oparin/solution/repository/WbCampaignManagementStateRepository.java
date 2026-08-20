package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.WbCampaignManagementState;

import java.util.List;

@Repository
public interface WbCampaignManagementStateRepository extends JpaRepository<WbCampaignManagementState, Long> {

    List<WbCampaignManagementState> findByCabinetId(Long cabinetId);
}
