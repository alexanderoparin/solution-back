package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.CabinetAbTestQuota;

import java.util.Optional;

@Repository
public interface CabinetAbTestQuotaRepository extends JpaRepository<CabinetAbTestQuota, Long> {

    Optional<CabinetAbTestQuota> findByCabinetId(Long cabinetId);

    @Query("""
            select q from CabinetAbTestQuota q
            where q.cabinetId = :cabinetId
            """)
    Optional<CabinetAbTestQuota> findForUpdate(@Param("cabinetId") Long cabinetId);
}
