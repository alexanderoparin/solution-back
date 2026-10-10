package ru.oparin.solution.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.oparin.solution.model.PromoCodeRedemption;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Репозиторий активаций промокодов.
 */
public interface PromoCodeRedemptionRepository extends JpaRepository<PromoCodeRedemption, Long> {

    boolean existsByUser_IdAndPromoCode_Id(Long userId, Long promoCodeId);

    /**
     * Удаляет все активации промокодов конкретного пользователя.
     *
     * @param userId идентификатор пользователя
     */
    @Modifying
    @Query("DELETE FROM PromoCodeRedemption r WHERE r.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    /**
     * Есть ли у пользователя хотя бы одна неистёкшая активация промокода.
     */
    @Query("""
            SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
            FROM PromoCodeRedemption r
            WHERE r.user.id = :userId
              AND r.expiresAt > :now
            """)
    boolean existsActiveByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * Native fallback на случай расхождения JPQL и TIMESTAMP в PostgreSQL.
     */
    @Query(value = """
            SELECT COUNT(*) > 0
            FROM promo_code_redemptions r
            WHERE r.user_id = :userId
              AND r.expires_at > :now
            """, nativeQuery = true)
    boolean existsActiveByUserIdNative(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * Последняя активная активация промокода пользователя.
     */
    @Query("""
            SELECT r FROM PromoCodeRedemption r
            JOIN FETCH r.promoCode p
            WHERE r.user.id = :userId
              AND r.expiresAt > :now
            ORDER BY r.expiresAt DESC
            """)
    Optional<PromoCodeRedemption> findFirstActiveByUserId(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now);

    @Query("""
            SELECT r FROM PromoCodeRedemption r
            JOIN FETCH r.promoCode p
            JOIN FETCH r.user u
            WHERE (:code IS NULL OR :code = '' OR UPPER(p.code) = UPPER(:code))
            ORDER BY r.redeemedAt DESC
            """)
    Page<PromoCodeRedemption> findAllForAdmin(@Param("code") String code, Pageable pageable);
}
