package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.ArticleNote;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с заметками к артикулам.
 */
@Repository
public interface ArticleNoteRepository extends JpaRepository<ArticleNote, Long> {

    /**
     * Находит все заметки для указанного артикула и продавца.
     *
     * @param nmId артикул WB
     * @param sellerId ID продавца
     * @return список заметок, отсортированных по дате создания (новые первыми)
     */
    @Query("SELECT n FROM ArticleNote n WHERE n.nmId = :nmId AND n.sellerId = :sellerId ORDER BY n.createdAt DESC")
    List<ArticleNote> findByNmIdAndSellerIdOrderByCreatedAtDesc(@Param("nmId") Long nmId, @Param("sellerId") Long sellerId);

    /**
     * Находит заметку по ID, артикулу и продавцу.
     *
     * @param id ID заметки
     * @param nmId артикул WB
     * @param sellerId ID продавца
     * @return заметка, если найдена
     */
    @Query("SELECT n FROM ArticleNote n WHERE n.id = :id AND n.nmId = :nmId AND n.sellerId = :sellerId")
    Optional<ArticleNote> findByIdAndNmIdAndSellerId(@Param("id") Long id, @Param("nmId") Long nmId, @Param("sellerId") Long sellerId);

    /**
     * Проверяет существование заметки по ID, артикулу и продавцу.
     *
     * @param id ID заметки
     * @param nmId артикул WB
     * @param sellerId ID продавца
     * @return true, если заметка существует
     */
    @Query("SELECT COUNT(n) > 0 FROM ArticleNote n WHERE n.id = :id AND n.nmId = :nmId AND n.sellerId = :sellerId")
    boolean existsByIdAndNmIdAndSellerId(@Param("id") Long id, @Param("nmId") Long nmId, @Param("sellerId") Long sellerId);
}

