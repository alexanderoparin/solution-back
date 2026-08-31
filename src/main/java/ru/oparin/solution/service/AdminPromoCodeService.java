package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.PageResponse;
import ru.oparin.solution.dto.PromoCodeAdminDto;
import ru.oparin.solution.dto.PromoCodeRedemptionAdminDto;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.PromoCode;
import ru.oparin.solution.model.PromoCodeRedemption;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.PromoCodeRedemptionRepository;
import ru.oparin.solution.repository.PromoCodeRepository;

import java.util.List;

/**
 * Админ-операции с промокодами и активациями.
 */
@Service
@RequiredArgsConstructor
public class AdminPromoCodeService {

    private final PromoCodeRepository promoCodeRepository;
    private final PromoCodeRedemptionRepository redemptionRepository;

    /**
     * Список промокодов для фильтра и будущего CRUD.
     */
    @Transactional(readOnly = true)
    public List<PromoCodeAdminDto> listPromoCodes(User admin) {
        requireAdmin(admin);
        return promoCodeRepository.findAll().stream()
                .map(this::toPromoDto)
                .toList();
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
                .maxRedemptionsPerUser(promo.getMaxRedemptionsPerUser())
                .maxRedemptionsTotal(promo.getMaxRedemptionsTotal())
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
