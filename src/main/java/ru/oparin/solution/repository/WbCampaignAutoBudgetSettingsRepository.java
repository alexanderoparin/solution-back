package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.WbCampaignAutoBudgetSettings;

import java.util.List;

@Repository
public interface WbCampaignAutoBudgetSettingsRepository extends JpaRepository<WbCampaignAutoBudgetSettings, Long> {

    List<WbCampaignAutoBudgetSettings> findByCabinetId(Long cabinetId);
}
