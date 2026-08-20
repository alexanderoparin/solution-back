package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.WbArticleGoal;

import java.util.Optional;

public interface WbArticleGoalRepository extends JpaRepository<WbArticleGoal, Long> {

    Optional<WbArticleGoal> findByCabinetIdAndNmId(Long cabinetId, Long nmId);
}
