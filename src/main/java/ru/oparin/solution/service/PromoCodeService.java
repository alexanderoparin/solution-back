package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.PromoCode;
import ru.oparin.solution.model.PromoCodeRedemption;
import ru.oparin.solution.model.PromoRedemptionSource;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.PromoCodeRedemptionRepository;
import ru.oparin.solution.repository.PromoCodeRepository;
import ru.oparin.solution.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Активация промокодов и проверка user-level доступа по промо.
 */
@Service
@RequiredArgsConstructor
public class PromoCodeService {

    private final PromoCodeRepository promoCodeRepository;
    private final PromoCodeRedemptionRepository redemptionRepository;
    private final UserRepository userRepository;

    /**
     * Активирует промокод для пользователя.
     *
     * @param code   код промо (без учёта регистра)
     * @param userId ID пользователя
     * @param source контекст активации
     * @return созданная активация
     */
    @Transactional
    public PromoCodeRedemption redeem(String code, Long userId, PromoRedemptionSource source) {
        String normalizedCode = normalizeCode(code);
        PromoCode promo = promoCodeRepository.findByCodeIgnoreCase(normalizedCode)
                .orElseThrow(() -> new UserException("Промокод не найден или недействителен", HttpStatus.BAD_REQUEST));

        validatePromoAvailable(promo);
        validateUserCanRedeem(promo, userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException("Пользователь не найден", HttpStatus.NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        PromoCodeRedemption redemption = PromoCodeRedemption.builder()
                .promoCode(promo)
                .user(user)
                .redeemedAt(now)
                .expiresAt(now.plusDays(promo.getDurationDays()))
                .source(source)
                .build();
        return redemptionRepository.save(redemption);
    }

    /**
     * Есть ли у пользователя активный (неистёкший) промо-доступ.
     */
    @Transactional(readOnly = true)
    public boolean hasActivePromo(Long userId) {
        if (userId == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (redemptionRepository.existsActiveByUserId(userId, now)) {
            return true;
        }
        return redemptionRepository.existsActiveByUserIdNative(userId, now);
    }

    /**
     * Активная активация промокода для профиля или админки.
     */
    @Transactional(readOnly = true)
    public Optional<PromoCodeRedemption> findActiveRedemption(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return redemptionRepository.findFirstActiveByUserId(userId, LocalDateTime.now());
    }

    /**
     * Удаляет все записи об активации промокодов для указанного пользователя.
     *
     * @param userId идентификатор пользователя
     */
    @Transactional
    public void deleteRedemptionsByUserId(Long userId) {
        if (userId == null) {
            return;
        }
        redemptionRepository.deleteByUserId(userId);
    }

    private void validatePromoAvailable(PromoCode promo) {
        if (!promo.isActive()) {
            throw new UserException("Промокод не найден или недействителен", HttpStatus.BAD_REQUEST);
        }
        LocalDateTime now = LocalDateTime.now();
        if (promo.getValidFrom() != null && now.isBefore(promo.getValidFrom())) {
            throw new UserException("Промокод ещё не активен", HttpStatus.BAD_REQUEST);
        }
        if (promo.getValidTo() != null && now.isAfter(promo.getValidTo())) {
            throw new UserException("Срок действия промокода истёк", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateUserCanRedeem(PromoCode promo, Long userId) {
        if (redemptionRepository.existsByUser_IdAndPromoCode_Id(userId, promo.getId())) {
            throw new UserException("Вы уже использовали этот промокод", HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new UserException("Укажите промокод", HttpStatus.BAD_REQUEST);
        }
        return code.trim().toUpperCase();
    }
}
