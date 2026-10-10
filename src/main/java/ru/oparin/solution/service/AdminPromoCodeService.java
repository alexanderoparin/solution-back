package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.CreatePromoCodeRequest;
import ru.oparin.solution.dto.PageResponse;
import ru.oparin.solution.dto.PromoCodeAdminDto;
import ru.oparin.solution.dto.PromoCodeRedemptionAdminDto;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.PromoCodeRedemptionRepository;
import ru.oparin.solution.repository.PromoCodeRepository;

import java.util.Comparator;
import java.util.List;

/**
 * Админ-операции с промокодами и активациями.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPromoCodeService {

    private final PromoCodeRepository promoCodeRepository;
    private final PromoCodeRedemptionRepository redemptionRepository;

    /**
     * Список промокодов (новые сверху).
     */
    @Transactional(readOnly = true)
    public List<PromoCodeAdminDto> listPromoCodes(User admin) {
        requireAdmin(admin);
        return promoCodeRepository.findAll().stream()
                .sorted(Comparator.comparing(PromoCode::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toPromoDto)
                .toList();
    }

    /**
     * Создание нового промокода.
     *
     * @param admin   администратор
     * @param request параметры промокода
     * @return созданный промокод
     */
    @Transactional
    public PromoCodeAdminDto createPromoCode(User admin, CreatePromoCodeRequest request) {
        requireAdmin(admin);
        String normalizedCode = normalizeCode(request.getCode());
        if (promoCodeRepository.findByCodeIgnoreCase(normalizedCode).isPresent()) {
            throw new UserException("Промокод с таким кодом уже существует", HttpStatus.CONFLICT);
        }
        if (request.getValidFrom() != null
                && request.getValidTo() != null
                && request.getValidTo().isBefore(request.getValidFrom())) {
            throw new UserException("Окончание действия не может быть раньше начала", HttpStatus.BAD_REQUEST);
        }

        PromoGrantType grantType = request.getGrantType() != null
                ? request.getGrantType()
                : PromoGrantType.FULL_ACCESS;

        boolean active = request.getActive() == null || Boolean.TRUE.equals(request.getActive());
        PromoCode saved = promoCodeRepository.save(PromoCode.builder()
                .code(normalizedCode)
                .description(blankToNull(request.getDescription()))
                .durationDays(request.getDurationDays())
                .grantType(grantType)
                .active(active)
                .validFrom(request.getValidFrom())
                .validTo(request.getValidTo())
                .build());
        log.info(
                "Админ id={} email={} создал промокод id={} code={} active={} durationDays={} grantType={} validFrom={} validTo={}",
                admin.getId(),
                admin.getEmail(),
                saved.getId(),
                saved.getCode(),
                saved.isActive(),
                saved.getDurationDays(),
                saved.getGrantType(),
                saved.getValidFrom(),
                saved.getValidTo());
        return toPromoDto(saved);
    }

    /**
     * Включение или выключение промокода.
     *
     * @param admin   администратор
     * @param promoId идентификатор промокода
     * @param active  новый статус
     * @return обновлённый промокод
     */
    @Transactional
    public PromoCodeAdminDto setActive(User admin, Long promoId, boolean active) {
        requireAdmin(admin);
        PromoCode promo = promoCodeRepository.findById(promoId)
                .orElseThrow(() -> new UserException("Промокод не найден", HttpStatus.NOT_FOUND));
        boolean previousActive = promo.isActive();
        promo.setActive(active);
        PromoCode saved = promoCodeRepository.save(promo);
        log.info(
                "Админ id={} email={} {} промокод id={} code={} (было active={})",
                admin.getId(),
                admin.getEmail(),
                active ? "включил" : "выключил",
                saved.getId(),
                saved.getCode(),
                previousActive);
        return toPromoDto(saved);
    }

    private static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new UserException("Укажите код промокода", HttpStatus.BAD_REQUEST);
        }
        return code.trim().toUpperCase();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * Постраничный список активаций промокодов.
     */
    @Transactional(readOnly = true)
    public PageResponse<PromoCodeRedemptionAdminDto> pageRedemptions(
            User admin,
            int page,
            int size,
            String code
    ) {
        requireAdmin(admin);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String filterCode = code != null && !code.isBlank() ? code.trim() : null;
        Page<PromoCodeRedemption> redemptionPage = redemptionRepository.findAllForAdmin(
                filterCode,
                PageRequest.of(safePage, safeSize));
        List<PromoCodeRedemptionAdminDto> rows = redemptionPage.getContent().stream()
                .map(this::toRedemptionDto)
                .toList();
        return PageResponse.<PromoCodeRedemptionAdminDto>builder()
                .content(rows)
                .totalElements(redemptionPage.getTotalElements())
                .totalPages(redemptionPage.getTotalPages())
                .size(redemptionPage.getSize())
                .number(redemptionPage.getNumber())
                .build();
    }

    private PromoCodeAdminDto toPromoDto(PromoCode promo) {
        return PromoCodeAdminDto.builder()
                .id(promo.getId())
                .code(promo.getCode())
                .description(promo.getDescription())
                .durationDays(promo.getDurationDays())
                .grantType(promo.getGrantType().name())
                .active(promo.isActive())
                .validFrom(promo.getValidFrom())
                .validTo(promo.getValidTo())
                .createdAt(promo.getCreatedAt())
                .build();
    }

    private PromoCodeRedemptionAdminDto toRedemptionDto(PromoCodeRedemption redemption) {
        return PromoCodeRedemptionAdminDto.builder()
                .id(redemption.getId())
                .email(redemption.getUser().getEmail())
                .promoCode(redemption.getPromoCode().getCode())
                .redeemedAt(redemption.getRedeemedAt())
                .expiresAt(redemption.getExpiresAt())
                .userRegisteredAt(redemption.getUser().getCreatedAt())
                .build();
    }

    private void requireAdmin(User admin) {
        if (admin == null || admin.getRole() != Role.ADMIN) {
            throw new UserException("Доступ только для ADMIN", HttpStatus.FORBIDDEN);
        }
    }
}
