package ru.oparin.solution.service.abtest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.WbAbTestRepository;
import ru.oparin.solution.repository.WbAbTestVariantRepository;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.events.WbApiEventService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Оркестратор: ставит в очередь статистику, ротацию и автостоп (без синхронных вызовов WB).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WbAbTestOrchestrator {

    private final WbAbTestRepository abTestRepository;
    private final WbAbTestVariantRepository abTestVariantRepository;
    private final WbAbTestService abTestService;
    private final WbApiEventService wbApiEventService;
    private final CabinetService cabinetService;

    /**
     * Тик: enqueue stats / rotate / finish.
     */
    @Transactional
    public void tick() {
        List<WbAbTest> enabled = abTestRepository.findByStatus(WbAbTestStatus.ENABLED);
        for (WbAbTest test : enabled) {
            try {
                processOne(test);
            } catch (Exception e) {
                log.warn("Ошибка обработки ab_test id={}: {}", test.getId(), e.getMessage());
            }
        }
    }

    private void processOne(WbAbTest test) {
        if (shouldEnqueueStats(test)) {
            wbApiEventService.enqueueWbAbTestStatsPoll(test.getCabinetId(), test.getId(), "AB_TEST_TICK");
        }

        if (shouldStopByDuration(test) || shouldStopByTrust(test)) {
            abTestService.enqueueFinish(test, test.getStopMode().name());
            return;
        }

        if (shouldRotate(test)) {
            abTestService.enqueueRotateToNext(test, test.getRotationMode().name());
        }
    }

    private boolean shouldEnqueueStats(WbAbTest test) {
        try {
            Cabinet cabinet = cabinetService.findByIdWithUserOrThrow(test.getCabinetId());
            if (!CabinetTokenType.effective(cabinet.getTokenType()).supportsFrequentFullstats()) {
                return test.getLastStatsAt() == null
                        || test.getLastStatsAt().isBefore(LocalDateTime.now().minusMinutes(55));
            }
        } catch (Exception e) {
            log.debug("Не удалось проверить тип токена для ab_test {}: {}", test.getId(), e.getMessage());
        }
        // Персональный: данные WB ~раз в 3 мин — не чаще.
        return test.getLastStatsAt() == null
                || test.getLastStatsAt().isBefore(LocalDateTime.now().minusMinutes(3));
    }

    private boolean shouldStopByDuration(WbAbTest test) {
        return test.getStopMode() == WbAbTestStopMode.BY_DURATION
                && test.getEndsAt() != null
                && !LocalDateTime.now().isBefore(test.getEndsAt());
    }

    private boolean shouldStopByTrust(WbAbTest test) {
        if (test.getStopMode() != WbAbTestStopMode.TRUST_US) {
            return false;
        }
        List<WbAbTestVariant> variants = abTestVariantRepository.findByWbAbTestIdOrderBySortOrderAsc(test.getId());
        return abTestService.shouldAutoStopTrustUs(test, variants);
    }

    private boolean shouldRotate(WbAbTest test) {
        if (test.getActiveVariantId() != null) {
            WbAbTestVariant active = abTestVariantRepository.findById(test.getActiveVariantId()).orElse(null);
            if (active != null && active.isPaused()) {
                return true;
            }
        }
        if (test.getRotationMode() == WbAbTestRotationMode.ROTATION_BY_INTERVAL) {
            Integer minutes = test.getRotationIntervalMinutes();
            if (minutes == null || minutes < 1) {
                return false;
            }
            LocalDateTime last = test.getLastRotatedAt() != null ? test.getLastRotatedAt() : test.getStartedAt();
            return last != null && !LocalDateTime.now().isBefore(last.plusMinutes(minutes));
        }
        if (test.getRotationMode() == WbAbTestRotationMode.ROTATION_BY_VIEWS) {
            Integer threshold = test.getRotationViewsThreshold();
            if (threshold == null || test.getActiveVariantId() == null) {
                return false;
            }
            WbAbTestVariant active = abTestVariantRepository.findById(test.getActiveVariantId()).orElse(null);
            if (active == null) {
                return false;
            }
            return active.getViews() - test.getActiveSinceViews() >= threshold;
        }
        return false;
    }
}
