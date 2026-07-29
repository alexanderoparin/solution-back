package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.oparin.solution.model.AbTestVariant;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий вариантов фото А/Б-теста.
 */
public interface AbTestVariantRepository extends JpaRepository<AbTestVariant, Long> {

    List<AbTestVariant> findByAbTestIdOrderBySortOrderAsc(Long abTestId);

    Optional<AbTestVariant> findByIdAndAbTestId(Long id, Long abTestId);

    void deleteByAbTestId(Long abTestId);

    @Query("SELECT v.storedFileName FROM AbTestVariant v WHERE v.storedFileName IS NOT NULL")
    List<String> findAllStoredFileNames();
}
