package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.WbCabinetAbTestQuota;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface WbCabinetAbTestQuotaRepository extends JpaRepository<WbCabinetAbTestQuota, Long> {

    Optional<WbCabinetAbTestQuota> findByCabinetId(Long cabinetId);

    List<WbCabinetAbTestQuota> findByCabinetIdIn(Collection<Long> cabinetIds);

    @Query("""
            select q from WbCabinetAbTestQuota q
            where q.cabinetId = :cabinetId
            """)
    Optional<WbCabinetAbTestQuota> findForUpdate(@Param("cabinetId") Long cabinetId);
}
