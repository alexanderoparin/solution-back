package ru.oparin.solution.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.WbArticleNote;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с заметками к артикулам.
 */
@Repository
public interface WbArticleNoteRepository extends JpaRepository<WbArticleNote, Long> {

    /**
     * Находит все заметки для указанного артикула и продавца.
     *
     * @param nmId артикул WB
     * @param sellerId ID продавца
     * @return список заметок, отсортированных по дате создания (новые первыми)
     */
    @Query("SELECT n FROM WbArticleNote n WHERE n.nmId = :nmId AND n.sellerId = :sellerId ORDER BY n.createdAt DESC")
    List<WbArticleNote> findByNmIdAndSellerIdOrderByCreatedAtDesc(@Param("nmId") Long nmId, @Param("sellerId") Long sellerId);

    /**
     * Находит заметку по ID, артикулу и продавцу.
     *
     * @param id ID заметки
     * @param nmId артикул WB
     * @param sellerId ID продавца
     * @return заметка, если найдена
     */
    @Query("SELECT n FROM WbArticleNote n WHERE n.id = :id AND n.nmId = :nmId AND n.sellerId = :sellerId")
    Optional<WbArticleNote> findByIdAndNmIdAndSellerId(@Param("id") Long id, @Param("nmId") Long nmId, @Param("sellerId") Long sellerId);

    /**
     * Проверяет существование заметки по ID, артикулу и продавцу.
     *
     * @param id ID заметки
     * @param nmId артикул WB
     * @param sellerId ID продавца
     * @return true, если заметка существует
     */
    @Query("SELECT COUNT(n) > 0 FROM WbArticleNote n WHERE n.id = :id AND n.nmId = :nmId AND n.sellerId = :sellerId")
    boolean existsByIdAndNmIdAndSellerId(@Param("id") Long id, @Param("nmId") Long nmId, @Param("sellerId") Long sellerId);

    /**
     * Находит все заметки для артикула и кабинета.
     */
    List<WbArticleNote> findByNmIdAndCabinetIdOrderByCreatedAtDesc(Long nmId, Long cabinetId);

    /**
     * Находит заметку по ID, артикулу и кабинету.
     */
    Optional<WbArticleNote> findByIdAndNmIdAndCabinetId(Long id, Long nmId, Long cabinetId);

    /**
     * Проверяет существование заметки по ID, артикулу и кабинету.
     */
    boolean existsByIdAndNmIdAndCabinetId(Long id, Long nmId, Long cabinetId);

    void deleteByCabinetId(Long cabinetId);

    /**
     * Выборка только ID по кабинету пачкой (для пакетного удаления по ключам).
     */
    @Query("SELECT n.id FROM WbArticleNote n WHERE n.cabinetId = :cabinetId")
    List<Long> findIdByCabinetId(@Param("cabinetId") Long cabinetId, Pageable pageable);
}

