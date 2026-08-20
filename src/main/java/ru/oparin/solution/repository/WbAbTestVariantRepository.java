package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.oparin.solution.model.WbAbTestVariant;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий вариантов фото А/Б-теста.
 */
public interface WbAbTestVariantRepository extends JpaRepository<WbAbTestVariant, Long> {

    List<WbAbTestVariant> findByAbTestIdOrderBySortOrderAsc(Long abTestId);

    Optional<WbAbTestVariant> findByIdAndAbTestId(Long id, Long abTestId);

    void deleteByAbTestId(Long abTestId);

    @Query("SELECT v.storedFileName FROM WbAbTestVariant v WHERE v.storedFileName IS NOT NULL")
    List<String> findAllStoredFileNames();
}
