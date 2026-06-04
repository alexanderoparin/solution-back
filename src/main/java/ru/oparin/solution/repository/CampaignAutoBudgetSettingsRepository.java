package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.CampaignAutoBudgetSettings;

import java.util.List;

@Repository
public interface CampaignAutoBudgetSettingsRepository extends JpaRepository<CampaignAutoBudgetSettings, Long> {

    List<CampaignAutoBudgetSettings> findByCabinetId(Long cabinetId);
}
