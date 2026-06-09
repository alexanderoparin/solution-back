package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.ArticleGoal;

import java.util.Optional;

public interface ArticleGoalRepository extends JpaRepository<ArticleGoal, Long> {

    Optional<ArticleGoal> findByCabinetIdAndNmId(Long cabinetId, Long nmId);
}
