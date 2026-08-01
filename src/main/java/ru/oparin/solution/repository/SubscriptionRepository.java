package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.PlanKind;
import ru.oparin.solution.model.Subscription;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    /**
     * Активная подписка кабинета заданного kind.
     */
    @Query("""
            select s from Subscription s
            left join fetch s.plan
            where s.cabinet.id = :cabinetId
              and s.plan.kind = :kind
              and s.status in :statuses
              and (s.expiresAt is null or s.expiresAt > :now)
            order by s.expiresAt desc nulls first
            """)
    Optional<Subscription> findFirstActiveByCabinetIdAndKind(
            @Param("cabinetId") Long cabinetId,
            @Param("kind") PlanKind kind,
            @Param("statuses") Collection<String> statuses,
            @Param("now") LocalDateTime now
    );

    /**
     * Активная подписка кабинета на план с префиксом кода (например campaign_).
     */
    @Query("""
            select s from Subscription s
            left join fetch s.plan
            where s.cabinet.id = :cabinetId
              and s.plan.code like concat(:planCodePrefix, '%')
              and s.status in :statuses
              and (s.expiresAt is null or s.expiresAt > :now)
            order by s.expiresAt desc nulls first
            """)
    Optional<Subscription> findFirstActiveByCabinetIdAndCodePrefix(
            @Param("cabinetId") Long cabinetId,
            @Param("planCodePrefix") String planCodePrefix,
            @Param("statuses") Collection<String> statuses,
            @Param("now") LocalDateTime now
    );

    @Query("""
            select s from Subscription s
            left join fetch s.plan
            where s.cabinet.id = :cabinetId
            order by s.expiresAt desc nulls first
            """)
    List<Subscription> findByCabinet_IdOrderByExpiresAtDesc(@Param("cabinetId") Long cabinetId);

    @Query("""
            select s from Subscription s
            left join fetch s.plan
            where s.user.id = :userId
            order by s.expiresAt desc nulls first
            """)
    List<Subscription> findByUser_IdOrderByExpiresAtDesc(@Param("userId") Long userId);

    @Query("""
            select s from Subscription s
            left join fetch s.plan
            where s.cabinet.id = :cabinetId
              and s.plan.code like concat(:planCodePrefix, '%')
              and s.expiresAt is not null
              and s.expiresAt <= :now
            order by s.expiresAt desc
            """)
    Optional<Subscription> findLastExpiredByCabinetIdAndCodePrefix(
            @Param("cabinetId") Long cabinetId,
            @Param("planCodePrefix") String planCodePrefix,
            @Param("now") LocalDateTime now
    );

    boolean existsByCabinet_IdAndPlan_Code(Long cabinetId, String planCode);

    boolean existsByUser_IdAndPlan_Code(Long userId, String planCode);

    /**
     * @deprecated используйте cabinet-scoped методы; оставлено для совместимости миграции.
     */
    @Deprecated
    @Query("""
            select s from Subscription s
            left join fetch s.plan
            where s.user.id = :userId
              and s.status in :statuses
              and (s.expiresAt is null or s.expiresAt > :now)
            order by s.expiresAt desc nulls first
            """)
    Optional<Subscription> findFirstActiveByUserId(
            @Param("userId") Long userId,
            @Param("statuses") Collection<String> statuses,
            @Param("now") LocalDateTime now
    );

    @Deprecated
    @Query("""
            select s from Subscription s
            left join fetch s.plan
            where s.user.id = :userId
              and s.plan.code like concat(:planCodePrefix, '%')
              and s.status in :statuses
              and (s.expiresAt is null or s.expiresAt > :now)
            order by s.expiresAt desc nulls first
            """)
    Optional<Subscription> findFirstActiveCampaignByUserId(
            @Param("userId") Long userId,
            @Param("planCodePrefix") String planCodePrefix,
            @Param("statuses") Collection<String> statuses,
            @Param("now") LocalDateTime now
    );

    @Deprecated
    @Query("""
            select s from Subscription s
            left join fetch s.plan
            where s.user.id = :userId
              and s.expiresAt is not null
              and s.expiresAt <= :now
            order by s.expiresAt desc
            """)
    Optional<Subscription> findLastExpiredByUserId(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now
    );

    @Deprecated
    @Query("""
            select s from Subscription s
            left join fetch s.plan
            where s.user.id = :userId
              and s.plan.code like concat(:planCodePrefix, '%')
              and s.expiresAt is not null
              and s.expiresAt <= :now
            order by s.expiresAt desc
            """)
    Optional<Subscription> findLastExpiredCampaignByUserId(
            @Param("userId") Long userId,
            @Param("planCodePrefix") String planCodePrefix,
            @Param("now") LocalDateTime now
    );
}
