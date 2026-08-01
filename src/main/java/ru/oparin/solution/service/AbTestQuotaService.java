package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.AbTestQuotaDto;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.CabinetAbTestQuota;
import ru.oparin.solution.model.PlanCodes;
import ru.oparin.solution.repository.CabinetAbTestQuotaRepository;
import ru.oparin.solution.repository.CabinetRepository;

/**
 * Квота запусков А/Б тестов на кабинет.
 */
@Service
@RequiredArgsConstructor
public class AbTestQuotaService {

    public static final String AB_TEST_QUOTA_REQUIRED = "AB_TEST_QUOTA_REQUIRED";

    private final CabinetAbTestQuotaRepository quotaRepository;
    private final CabinetRepository cabinetRepository;
    private final CabinetEntitlementService cabinetEntitlementService;

    /**
     * Создаёт стартовую квоту (3 бесплатных, ещё не активирована) для нового кабинета.
     */
    @Transactional
    public void ensureQuota(Cabinet cabinet) {
        if (quotaRepository.findByCabinetId(cabinet.getId()).isPresent()) {
            return;
        }
        quotaRepository.save(CabinetAbTestQuota.builder()
                .cabinet(cabinet)
                .remaining(PlanCodes.AB_TEST_FREE_QUOTA)
                .usedStarts(0)
                .includedFree(PlanCodes.AB_TEST_FREE_QUOTA)
                .activated(false)
                .build());
    }

    @Transactional(readOnly = true)
    public AbTestQuotaDto getQuotaDto(Cabinet cabinet) {
        if (cabinetEntitlementService.hasUnlimitedAccess(cabinet)) {
            return AbTestQuotaDto.builder()
                    .remaining(null)
                    .usedStarts(resolveUsed(cabinet.getId()))
                    .includedFree(PlanCodes.AB_TEST_FREE_QUOTA)
                    .unlimited(true)
                    .activated(true)
                    .build();
        }
        CabinetAbTestQuota quota = getOrCreate(cabinet);
        return AbTestQuotaDto.builder()
                .remaining(quota.getRemaining())
                .usedStarts(quota.getUsedStarts())
                .includedFree(quota.getIncludedFree())
                .unlimited(false)
                .activated(Boolean.TRUE.equals(quota.getActivated()))
                .build();
    }

    /**
     * Можно ли создавать/стартовать А/Б: PRO/agency или активированная квота с remaining &gt; 0.
     */
    @Transactional(readOnly = true)
    public boolean canStartAbTest(Cabinet cabinet) {
        if (cabinetEntitlementService.hasUnlimitedAccess(cabinet)) {
            return true;
        }
        CabinetAbTestQuota quota = getOrCreate(cabinet);
        return Boolean.TRUE.equals(quota.getActivated())
                && quota.getRemaining() != null
                && quota.getRemaining() > 0;
    }

    /**
     * Явно подключает 3 бесплатных теста (если ещё не активировано).
     */
    @Transactional
    public AbTestQuotaDto activateFreeQuota(Cabinet cabinet) {
        if (cabinetEntitlementService.hasUnlimitedAccess(cabinet)) {
            return getQuotaDto(cabinet);
        }
        CabinetAbTestQuota quota = getOrCreate(cabinet);
        if (!Boolean.TRUE.equals(quota.getActivated())) {
            quota.setActivated(true);
            if (quota.getRemaining() == null || quota.getRemaining() < PlanCodes.AB_TEST_FREE_QUOTA) {
                // если ещё не тратили и не покупали — гарантируем стартовые 3
                if (quota.getUsedStarts() == null || quota.getUsedStarts() == 0) {
                    quota.setRemaining(Math.max(
                            quota.getRemaining() != null ? quota.getRemaining() : 0,
                            PlanCodes.AB_TEST_FREE_QUOTA
                    ));
                }
            }
            quotaRepository.save(quota);
        }
        return getQuotaDto(cabinet);
    }

    /**
     * Списывает 1 запуск (после создания / успешного старта).
     */
    @Transactional
    public void consumeStart(Cabinet cabinet) {
        if (cabinetEntitlementService.hasUnlimitedAccess(cabinet)) {
            CabinetAbTestQuota quota = getOrCreate(cabinet);
            quota.setUsedStarts(quota.getUsedStarts() + 1);
            quota.setActivated(true);
            quotaRepository.save(quota);
            return;
        }
        CabinetAbTestQuota quota = getOrCreate(cabinet);
        if (!Boolean.TRUE.equals(quota.getActivated())
                || quota.getRemaining() == null
                || quota.getRemaining() <= 0) {
            throw new UserException(
                    "Недостаточно квоты А/Б тестов",
                    HttpStatus.PAYMENT_REQUIRED
            );
        }
        quota.setRemaining(quota.getRemaining() - 1);
        quota.setUsedStarts(quota.getUsedStarts() + 1);
        quotaRepository.save(quota);
    }

    /**
     * Начисляет кредиты после покупки пакета (или админского действия) и активирует услугу.
     */
    @Transactional
    public void addCredits(Cabinet cabinet, int credits) {
        if (credits <= 0) {
            return;
        }
        CabinetAbTestQuota quota = getOrCreate(cabinet);
        quota.setRemaining(quota.getRemaining() + credits);
        quota.setActivated(true);
        quotaRepository.save(quota);
    }

    private CabinetAbTestQuota getOrCreate(Cabinet cabinet) {
        return quotaRepository.findByCabinetId(cabinet.getId())
                .orElseGet(() -> {
                    Cabinet managed = cabinetRepository.findById(cabinet.getId())
                            .orElseThrow(() -> new UserException("Кабинет не найден", HttpStatus.NOT_FOUND));
                    return quotaRepository.save(CabinetAbTestQuota.builder()
                            .cabinet(managed)
                            .remaining(PlanCodes.AB_TEST_FREE_QUOTA)
                            .usedStarts(0)
                            .includedFree(PlanCodes.AB_TEST_FREE_QUOTA)
                            .activated(false)
                            .build());
                });
    }

    private int resolveUsed(Long cabinetId) {
        return quotaRepository.findByCabinetId(cabinetId)
                .map(CabinetAbTestQuota::getUsedStarts)
                .orElse(0);
    }
}
