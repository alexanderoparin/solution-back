package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.FeedbacksSyncPageCheckpoint;

public interface FeedbacksSyncPageCheckpointRepository extends JpaRepository<FeedbacksSyncPageCheckpoint, Long> {

    boolean existsByRunIdAndIsAnsweredAndSkipValue(Long runId, Boolean isAnswered, Integer skipValue);

    void deleteByRunId(Long runId);
}
