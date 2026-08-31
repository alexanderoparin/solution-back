package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.*;
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
     * Есть ли у пользователя активный промо-доступ типа FULL_ACCESS.
     */
    @Transactional(readOnly = true)
    public boolean hasActiveFullAccess(Long userId) {
        if (userId == null) {
            return false;
        }
        return redemptionRepository.existsActiveFullAccess(
                userId, PromoGrantType.FULL_ACCESS, LocalDateTime.now());
    }

    /**
     * Активная активация FULL_ACCESS для профиля или админки.
     */
    @Transactional(readOnly = true)
    public Optional<PromoCodeRedemption> findActiveFullAccessRedemption(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return redemptionRepository.findFirstActiveByUserIdAndGrantType(
                userId, PromoGrantType.FULL_ACCESS, LocalDateTime.now());
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
        if (promo.getMaxRedemptionsTotal() != null) {
            long total = redemptionRepository.countByPromoCodeId(promo.getId());
            if (total >= promo.getMaxRedemptionsTotal()) {
                throw new UserException("Лимит использований промокода исчерпан", HttpStatus.BAD_REQUEST);
            }
        }
    }

    private void validateUserCanRedeem(PromoCode promo, Long userId) {
        if (promo.getMaxRedemptionsPerUser() != null && promo.getMaxRedemptionsPerUser() <= 0) {
            throw new UserException("Промокод недоступен для активации", HttpStatus.BAD_REQUEST);
        }
        if (promo.getMaxRedemptionsPerUser() != null
                && redemptionRepository.existsByUserIdAndPromoCodeId(userId, promo.getId())) {
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
