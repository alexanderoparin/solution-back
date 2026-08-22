package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.oparin.solution.model.OzonApiEvent;
import ru.oparin.solution.model.OzonApiEventStatus;
import ru.oparin.solution.model.OzonApiEventType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface OzonApiEventRepository extends JpaRepository<OzonApiEvent, Long> {

    @Query(value = """
            SELECT DISTINCT ON (e.cabinet_id, e.event_type) e.id
              FROM solution.ozon_api_events e
             WHERE e.status IN (:statuses)
               AND e.next_attempt_at <= :now
             ORDER BY e.cabinet_id, e.event_type, e.priority DESC, e.next_attempt_at ASC, e.id ASC
            """, nativeQuery = true)
    List<Long> findReadyEventIdsOnePerCabinetAndType(
            @Param("statuses") Collection<String> statuses,
            @Param("now") LocalDateTime now
    );

    @Query("""
            select e
            from OzonApiEvent e
            join fetch e.cabinet
            where e.id in :ids
            """)
    List<OzonApiEvent> findAllByIdInWithCabinet(@Param("ids") Collection<Long> ids);

    boolean existsByDedupKeyAndStatusIn(String dedupKey, Collection<OzonApiEventStatus> statuses);

    List<OzonApiEvent> findByStatusAndStartedAtBefore(OzonApiEventStatus status, LocalDateTime startedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OzonApiEvent e
               set e.status = :runningStatus,
                   e.startedAt = :now,
                   e.updatedAt = :now
             where e.id = :eventId
               and e.status in :fromStatuses
               and not exists (
                     select 1
                       from OzonApiEvent r
                      where r.cabinet.id = :cabinetId
                        and r.eventType = :eventType
                        and r.status = :runningStatus
                        and r.id <> :eventId
                 )
            """)
    int tryMarkRunning(
            @Param("eventId") Long eventId,
            @Param("cabinetId") Long cabinetId,
            @Param("eventType") OzonApiEventType eventType,
            @Param("fromStatuses") Collection<OzonApiEventStatus> fromStatuses,
            @Param("runningStatus") OzonApiEventStatus runningStatus,
            @Param("now") LocalDateTime now
    );
}
