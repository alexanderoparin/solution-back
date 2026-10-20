package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.WbAbTestQuotaDto;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.User;
import ru.oparin.solution.model.UserAbTestQuota;
import ru.oparin.solution.repository.UserAbTestQuotaRepository;
import ru.oparin.solution.repository.UserRepository;

/**
 * Квота запусков А/Б тестов на пользователя.
 * Безлимит на кабинете при PRO / agency / промо.
 */
@Service
@RequiredArgsConstructor
public class WbAbTestQuotaService {

    public static final String AB_TEST_QUOTA_REQUIRED = "AB_TEST_QUOTA_REQUIRED";

    private final UserAbTestQuotaRepository quotaRepository;
    private final UserRepository userRepository;
    private final CabinetEntitlementService cabinetEntitlementService;

    /**
     * Гарантирует строку квоты владельца кабинета (при создании кабинета).
     */
    @Transactional
    public void ensureQuota(Cabinet cabinet) {
        if (cabinet == null || cabinet.getUser() == null || cabinet.getUser().getId() == null) {
            return;
        }
        ensureUserQuota(cabinet.getUser().getId());
    }

    /**
     * Гарантирует строку квоты пользователя.
     */
    @Transactional
    public UserAbTestQuota ensureUserQuota(Long userId) {
        return quotaRepository.findById(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new UserException("Пользователь не найден", HttpStatus.NOT_FOUND));
            return quotaRepository.save(UserAbTestQuota.builder()
                    .user(user)
                    .remaining(0)
                    .usedStarts(0)
                    .build());
        });
    }

    @Transactional(readOnly = true)
    public WbAbTestQuotaDto getQuotaDto(Cabinet cabinet) {
        return getQuotaDto(cabinet, null);
    }

    /**
     * Квота для UI кабинета: безлимит при PRO, иначе remaining владельца.
     */
    @Transactional(readOnly = true)
    public WbAbTestQuotaDto getQuotaDto(Cabinet cabinet, User actor) {
        if (hasUnlimited(cabinet, actor)) {
            Long ownerId = resolveOwnerId(cabinet);
            return WbAbTestQuotaDto.builder()
                    .remaining(null)
                    .usedStarts(ownerId != null ? resolveUsed(ownerId) : 0)
                    .includedFree(0)
                    .unlimited(true)
                    .activated(true)
                    .build();
        }
        Long ownerId = resolveOwnerId(cabinet);
        if (ownerId == null) {
            return WbAbTestQuotaDto.builder()
                    .remaining(0)
                    .usedStarts(0)
                    .includedFree(0)
                    .unlimited(false)
                    .activated(false)
                    .build();
        }
        UserAbTestQuota quota = quotaRepository.findById(ownerId).orElse(null);
        int remaining = quota != null && quota.getRemaining() != null ? quota.getRemaining() : 0;
        int used = quota != null && quota.getUsedStarts() != null ? quota.getUsedStarts() : 0;
        boolean activated = remaining > 0 || used > 0;
        return WbAbTestQuotaDto.builder()
                .remaining(remaining)
                .usedStarts(used)
                .includedFree(0)
                .unlimited(false)
                .activated(activated)
                .build();
    }

    /**
     * Квота пользователя (для страницы подписок, без привязки к кабинету).
     */
    @Transactional(readOnly = true)
    public WbAbTestQuotaDto getUserQuotaDto(Long userId) {
        if (userId == null) {
            return WbAbTestQuotaDto.builder()
                    .remaining(0)
                    .usedStarts(0)
                    .includedFree(0)
                    .unlimited(false)
                    .activated(false)
                    .build();
        }
        UserAbTestQuota quota = quotaRepository.findById(userId).orElse(null);
        int remaining = quota != null && quota.getRemaining() != null ? quota.getRemaining() : 0;
        int used = quota != null && quota.getUsedStarts() != null ? quota.getUsedStarts() : 0;
        return WbAbTestQuotaDto.builder()
                .remaining(remaining)
                .usedStarts(used)
                .includedFree(0)
                .unlimited(false)
                .activated(remaining > 0 || used > 0)
                .build();
    }

    @Transactional(readOnly = true)
    public boolean canStartWbAbTest(Cabinet cabinet) {
        return canStartWbAbTest(cabinet, null);
    }

    @Transactional(readOnly = true)
    public boolean canStartWbAbTest(Cabinet cabinet, User actor) {
        if (hasUnlimited(cabinet, actor)) {
            return true;
        }
        Long ownerId = resolveOwnerId(cabinet);
        if (ownerId == null) {
            return false;
        }
        return quotaRepository.findById(ownerId)
                .map(q -> q.getRemaining() != null && q.getRemaining() > 0)
                .orElse(false);
    }

    /**
     * Списывает 1 запуск с квоты владельца кабинета (или только used при безлимите).
     */
    @Transactional
    public void consumeStart(Cabinet cabinet) {
        consumeStart(cabinet, null);
    }

    @Transactional
    public void consumeStart(Cabinet cabinet, User actor) {
        Long ownerId = resolveOwnerId(cabinet);
        if (ownerId == null) {
            throw new UserException("Недостаточно квоты А/Б тестов", HttpStatus.PAYMENT_REQUIRED);
        }
        UserAbTestQuota quota = ensureUserQuota(ownerId);
        if (hasUnlimited(cabinet, actor)) {
            quota.setUsedStarts((quota.getUsedStarts() != null ? quota.getUsedStarts() : 0) + 1);
            quotaRepository.save(quota);
            return;
        }
        if (quota.getRemaining() == null || quota.getRemaining() <= 0) {
            throw new UserException(
                    "Недостаточно квоты А/Б тестов",
                    HttpStatus.PAYMENT_REQUIRED
            );
        }
        quota.setRemaining(quota.getRemaining() - 1);
        quota.setUsedStarts((quota.getUsedStarts() != null ? quota.getUsedStarts() : 0) + 1);
        quotaRepository.save(quota);
    }

    /**
     * Начисляет кредиты пользователю после покупки пакета.
     */
    @Transactional
    public void addCreditsToUser(Long userId, int credits) {
        if (credits <= 0 || userId == null) {
            return;
        }
        UserAbTestQuota quota = ensureUserQuota(userId);
        quota.setRemaining((quota.getRemaining() != null ? quota.getRemaining() : 0) + credits);
        quotaRepository.save(quota);
    }

    /**
     * @deprecated квота больше не на кабинете; используйте {@link #addCreditsToUser}.
     */
    @Deprecated
    @Transactional
    public void addCredits(Cabinet cabinet, int credits) {
        if (cabinet == null || cabinet.getUser() == null) {
            return;
        }
        addCreditsToUser(cabinet.getUser().getId(), credits);
    }

    private Long resolveOwnerId(Cabinet cabinet) {
        if (cabinet == null || cabinet.getUser() == null) {
            return null;
        }
        return cabinet.getUser().getId();
    }

    private int resolveUsed(Long userId) {
        return quotaRepository.findById(userId)
                .map(UserAbTestQuota::getUsedStarts)
                .orElse(0);
    }

    private boolean hasUnlimited(Cabinet cabinet, User actor) {
        if (actor != null) {
            return cabinetEntitlementService.hasUnlimitedAccess(cabinet, actor);
        }
        return cabinetEntitlementService.hasUnlimitedAccess(cabinet);
    }
}
