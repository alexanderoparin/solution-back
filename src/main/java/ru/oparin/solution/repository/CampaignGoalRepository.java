package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.CampaignGoal;

import java.util.Optional;

public interface CampaignGoalRepository extends JpaRepository<CampaignGoal, Long> {

    Optional<CampaignGoal> findByCabinetIdAndCampaignId(Long cabinetId, Long campaignId);
}
