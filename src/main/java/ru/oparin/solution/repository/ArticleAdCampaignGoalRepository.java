package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.ArticleAdCampaignGoal;

import java.util.Optional;

public interface ArticleAdCampaignGoalRepository extends JpaRepository<ArticleAdCampaignGoal, Long> {

    Optional<ArticleAdCampaignGoal> findByCabinetIdAndNmId(Long cabinetId, Long nmId);
}
