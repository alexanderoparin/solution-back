package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.WbAbTestCampaign;

import java.util.List;

/**
 * Репозиторий РК, привязанных к А/Б-тесту.
 */
public interface WbAbTestCampaignRepository extends JpaRepository<WbAbTestCampaign, Long> {

    List<WbAbTestCampaign> findByAbTestId(Long abTestId);

    void deleteByAbTestId(Long abTestId);
}
