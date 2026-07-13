package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.oparin.solution.model.CabinetAccessGrant;
import ru.oparin.solution.model.CabinetAccessGrantStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CabinetAccessGrantRepository extends JpaRepository<CabinetAccessGrant, Long> {

    List<CabinetAccessGrant> findByCabinet_IdOrderByCreatedAtDesc(Long cabinetId);

    List<CabinetAccessGrant> findByUser_IdAndStatus(Long userId, CabinetAccessGrantStatus status);

    @Query("""
            select g from CabinetAccessGrant g
            join fetch g.cabinet c
            join fetch c.user
            where g.user.id = :userId
              and g.status = :status
              and (g.validUntil is null or g.validUntil > :now)
            order by c.name asc
            """)
    List<CabinetAccessGrant> findActiveGrantedForUser(
            @Param("userId") Long userId,
            @Param("status") CabinetAccessGrantStatus status,
            @Param("now") LocalDateTime now
    );

    Optional<CabinetAccessGrant> findByCabinet_IdAndUser_Id(Long cabinetId, Long userId);

    boolean existsByCabinet_IdAndUser_IdAndStatus(Long cabinetId, Long userId, CabinetAccessGrantStatus status);
}
