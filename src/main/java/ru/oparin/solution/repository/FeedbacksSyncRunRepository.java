package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.FeedbacksSyncRun;

public interface FeedbacksSyncRunRepository extends JpaRepository<FeedbacksSyncRun, Long> {
}
