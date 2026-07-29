package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.AbTestCampaign;

import java.util.List;

/**
 * Репозиторий РК, привязанных к А/Б-тесту.
 */
public interface AbTestCampaignRepository extends JpaRepository<AbTestCampaign, Long> {

    List<AbTestCampaign> findByAbTestId(Long abTestId);

    void deleteByAbTestId(Long abTestId);
}
