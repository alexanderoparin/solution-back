package ru.oparin.solution.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.oparin.solution.model.PromoCodeRedemption;
import ru.oparin.solution.model.PromoGrantType;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Репозиторий активаций промокодов.
 */
public interface PromoCodeRedemptionRepository extends JpaRepository<PromoCodeRedemption, Long> {

    long countByPromoCodeId(Long promoCodeId);

    boolean existsByUser_IdAndPromoCode_Id(Long userId, Long promoCodeId);

    @Query("""
            SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
            FROM PromoCodeRedemption r
            JOIN r.promoCode p
            WHERE r.user.id = :userId
              AND p.grantType = :grantType
              AND r.expiresAt > :now
            """)
    boolean existsActiveFullAccess(@Param("userId") Long userId,
                                   @Param("grantType") PromoGrantType grantType,
                                   @Param("now") LocalDateTime now);

    /**
     * Native fallback: активный FULL_ACCESS (на случай расхождения enum/JPQL и TIMESTAMP в PostgreSQL).
     */
    @Query(value = """
            SELECT COUNT(*) > 0
            FROM promo_code_redemptions r
            INNER JOIN promo_codes p ON p.id = r.promo_code_id
            WHERE r.user_id = :userId
              AND p.grant_type = 'FULL_ACCESS'
              AND r.expires_at > :now
            """, nativeQuery = true)
    boolean existsActiveFullAccessNative(@Param("userId") Long userId,
                                         @Param("now") LocalDateTime now);

    @Query("""
            SELECT r FROM PromoCodeRedemption r
            JOIN FETCH r.promoCode p
            WHERE r.user.id = :userId
              AND p.grantType = :grantType
              AND r.expiresAt > :now
            ORDER BY r.expiresAt DESC
            """)
    Optional<PromoCodeRedemption> findFirstActiveByUserIdAndGrantType(
            @Param("userId") Long userId,
            @Param("grantType") PromoGrantType grantType,
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
