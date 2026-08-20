package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.WbCampaignGoal;

import java.util.Optional;

public interface WbCampaignGoalRepository extends JpaRepository<WbCampaignGoal, Long> {

    Optional<WbCampaignGoal> findByCabinetIdAndCampaignId(Long cabinetId, Long campaignId);
}
