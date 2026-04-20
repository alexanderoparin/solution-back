package ru.oparin.solution.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.model.WbApiEventStatus;
import ru.oparin.solution.model.WbApiEventType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface WbApiEventRepository extends JpaRepository<WbApiEvent, Long> {

    @Query("""
            select e
            from WbApiEvent e
            join fetch e.cabinet
            where e.status in :statuses
              and e.nextAttemptAt <= :now
            order by e.priority desc, e.nextAttemptAt asc, e.createdAt asc
            """)
    List<WbApiEvent> findReadyEvents(
            @Param("statuses") Collection<WbApiEventStatus> statuses,
            @Param("now") LocalDateTime now
    );

    @Query("""
            select e
            from WbApiEvent e
            where (:status is null or e.status = :status)
              and (:eventType is null or e.eventType = :eventType)
              and (:cabinetId is null or e.cabinet.id = :cabinetId)
            """)
    Page<WbApiEvent> findAdminEvents(
            @Param("status") WbApiEventStatus status,
            @Param("eventType") WbApiEventType eventType,
            @Param("cabinetId") Long cabinetId,
            Pageable pageable
    );

    boolean existsByDedupKeyAndStatusIn(String dedupKey, Collection<WbApiEventStatus> statuses);

    boolean existsByCabinet_IdAndEventTypeAndStatusIn(Long cabinetId, WbApiEventType eventType, Collection<WbApiEventStatus> statuses);

    @Query("""
            select case when count(e) > 0 then true else false end
              from WbApiEvent e
             where e.cabinet.id = :cabinetId
               and e.eventType in :eventTypes
               and e.status in :statuses
               and (:excludeEventId is null or e.id <> :excludeEventId)
            """)
    boolean existsByCabinet_IdAndEventTypeInAndStatusInExcludingEventId(
            @Param("cabinetId") Long cabinetId,
            @Param("eventTypes") Collection<WbApiEventType> eventTypes,
            @Param("statuses") Collection<WbApiEventStatus> statuses,
            @Param("excludeEventId") Long excludeEventId
    );

    List<WbApiEvent> findByStatusAndStartedAtBefore(WbApiEventStatus status, LocalDateTime startedAt);

    List<WbApiEvent> findByStatus(WbApiEventStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update WbApiEvent e
               set e.status = :runningStatus,
                   e.startedAt = :now,
                   e.updatedAt = :now
             where e.id = :eventId
               and e.status in :fromStatuses
               and not exists (
                     select 1
                       from WbApiEvent r
                      where r.cabinet.id = :cabinetId
                        and r.eventType = :eventType
                        and r.status = :runningStatus
                        and r.id <> :eventId
                 )
            """)
    int tryMarkRunning(
            @Param("eventId") Long eventId,
            @Param("cabinetId") Long cabinetId,
            @Param("eventType") WbApiEventType eventType,
            @Param("fromStatuses") Collection<WbApiEventStatus> fromStatuses,
            @Param("runningStatus") WbApiEventStatus runningStatus,
            @Param("now") LocalDateTime now
    );

    long deleteByStatusAndFinishedAtBefore(WbApiEventStatus status, LocalDateTime finishedAt);

    long countByStatus(WbApiEventStatus status);

    @Query("""
            select e.eventType, count(e)
              from WbApiEvent e
             where (:status is null or e.status = :status)
             group by e.eventType
            """)
    List<Object[]> countGroupedByEventType(@Param("status") WbApiEventStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update WbApiEvent e
               set e.status = :toStatus,
                   e.nextAttemptAt = :now,
                   e.lastError = null,
                   e.finishedAt = null,
                   e.updatedAt = :now
             where e.status = :fromStatus
            """)
    int bulkRetryByStatus(
            @Param("fromStatus") WbApiEventStatus fromStatus,
            @Param("toStatus") WbApiEventStatus toStatus,
            @Param("now") LocalDateTime now
    );

    @Query("""
            select case when count(e) > 0 then true else false end
              from WbApiEvent e
             where e.cabinet.id = :cabinetId
               and e.eventType = :eventType
               and e.status in :statuses
               and e.dedupKey like concat(:prefix, '%')
            """)
    boolean existsByCabinet_IdAndEventTypeAndStatusInAndDedupKeyPrefix(
            @Param("cabinetId") Long cabinetId,
            @Param("eventType") WbApiEventType eventType,
            @Param("statuses") Collection<WbApiEventStatus> statuses,
            @Param("prefix") String prefix
    );

    @Query("""
            select case when count(e) > 0 then true else false end
              from WbApiEvent e
             where e.cabinet.id = :cabinetId
               and e.eventType = :eventType
               and e.status in :statuses
               and e.dedupKey like concat(:prefix, '%')
               and e.id <> :excludeEventId
            """)
    boolean existsOtherByCabinet_IdAndEventTypeAndStatusInAndDedupKeyPrefix(
            @Param("cabinetId") Long cabinetId,
            @Param("eventType") WbApiEventType eventType,
            @Param("statuses") Collection<WbApiEventStatus> statuses,
            @Param("prefix") String prefix,
            @Param("excludeEventId") Long excludeEventId
    );
}
