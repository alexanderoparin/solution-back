package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.oparin.solution.model.FeedbacksSyncAccumulator;
import ru.oparin.solution.model.FeedbacksSyncAccumulatorId;

import java.util.Collection;
import java.util.List;

public interface FeedbacksSyncAccumulatorRepository extends JpaRepository<FeedbacksSyncAccumulator, FeedbacksSyncAccumulatorId> {

    @Query("""
            select a
              from FeedbacksSyncAccumulator a
             where a.id.runId = :runId
               and a.id.nmId in :nmIds
            """)
    List<FeedbacksSyncAccumulator> findByRunIdAndNmIdIn(@Param("runId") Long runId, @Param("nmIds") Collection<Long> nmIds);

    @Query("""
            select a
              from FeedbacksSyncAccumulator a
             where a.id.runId = :runId
            """)
    List<FeedbacksSyncAccumulator> findByRunId(@Param("runId") Long runId);

    void deleteById_RunId(Long runId);
}
