package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.oparin.solution.dto.analytics.PromotionControlCapabilitiesDto;
import ru.oparin.solution.dto.wb.WbPromotionBudgetDepositRequest;
import ru.oparin.solution.dto.wb.WbPromotionBudgetResponse;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.WbCampaignAutoBudgetSettings;
import ru.oparin.solution.model.WbCampaignManagementState;
import ru.oparin.solution.repository.WbCampaignAutoBudgetSettingsRepository;
import ru.oparin.solution.repository.WbCampaignManagementStateRepository;
import ru.oparin.solution.service.WbPromotionCampaignControlWriteService;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Автопополнение бюджета РК. HTTP к WB выполняется без внешней длинной транзакции,
 * чтобы не удерживать соединение Hikari на время deposit/budget.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WbCampaignAutoTopUpService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");

    private final WbCampaignAutoBudgetSettingsRepository autoBudgetRepository;
    private final WbCampaignManagementStateRepository stateRepository;
    private final WbPromotionCampaignControlWriteService promotionControlWriteService;
    private final WbCampaignBudgetFetchService budgetFetchService;
    private final WbCampaignChangeLogService changeLogService;
    private final WbCampaignBudgetTimelineService timelineService;
    private final WbCampaignStartBudgetGuard startBudgetGuard;
    private final WbCampaignBudgetDepositService budgetDepositService;

    /**
     * Автопополнение включено и настроено (сумма и порог заданы).
     */
    public boolean isAutoTopUpEnabled(Long advertId) {
        return autoBudgetRepository.findById(advertId)
                .filter(WbCampaignAutoBudgetSettings::isEnabled)
                .filter(s -> s.getTopUpAmount() != null && s.getThresholdRub() != null)
                .isPresent();
    }

    /**
     * Пополняет бюджет при необходимости и сохраняет учёт (журнал, timeline, состояние слота).
     *
     * @return сумма пополнения в рублях, если deposit выполнен
     */
    public Optional<Integer> tryTopUpInNewTransaction(Long advertId, Long cabinetId, Cabinet cabinet) {
        PromotionControlCapabilitiesDto capabilities = promotionControlWriteService.getCapabilities(cabinet);
        if (!capabilities.canControl()) {
            log.debug(
                    "Автопополнение advertId={} пропущено: нет прав управления РК (кабинет {}). {}",
                    advertId,
                    cabinetId,
                    capabilities.message()
            );
            return Optional.empty();
        }

        Optional<WbCampaignAutoBudgetSettings> settingsOpt = autoBudgetRepository.findById(advertId);
        if (settingsOpt.isEmpty()) {
            return Optional.empty();
        }
        WbCampaignAutoBudgetSettings settings = settingsOpt.get();
        if (!settings.isEnabled() || settings.getTopUpAmount() == null || settings.getThresholdRub() == null) {
            return Optional.empty();
        }

        WbCampaignManagementState state = stateRepository.findById(advertId).orElse(null);
        if (state == null) {
            return Optional.empty();
        }

        LocalDate today = LocalDate.now(ZONE);
        resetTopUpCounterIfNewDay(state, today);
        if (settings.getMaxTopUpsPerDay() != null && state.getTopUpsTodayCount() >= settings.getMaxTopUpsPerDay()) {
            stateRepository.save(state);
            return Optional.empty();
        }

        // Без внешней @Transactional: HTTP budget не держит соединение БД.
        Optional<Integer> budgetTotal = budgetFetchService.fetchBudgetForDecision(cabinet, advertId, state);
        if (budgetTotal.isEmpty()) {
            stateRepository.save(state);
            return Optional.empty();
        }
        if (budgetTotal.get() >= settings.getThresholdRub()) {
            stateRepository.save(state);
            return Optional.empty();
        }

        int budgetBeforeTopUp = budgetTotal.get();
        int topUpAmount = settings.getTopUpAmount();
        int primarySourceType = settings.getSourceType() != null ? settings.getSourceType() : 1;
        Integer backupSourceType = settings.getBackupSourceType();

        try {
            DepositAttemptResult attempt = depositWithOptionalBackup(
                    cabinet, advertId, settings, topUpAmount, primarySourceType, backupSourceType);
            WbPromotionBudgetResponse depositResponse = attempt.depositResult().getResponse();
            int budgetAfterTopUp = budgetFetchService.resolveBudgetAfterTopUp(
                    budgetBeforeTopUp, topUpAmount, depositResponse);
            budgetFetchService.storeBudgetTotal(state, advertId, cabinetId, budgetAfterTopUp);
            startBudgetGuard.clearBlockIfBudgetAvailable(state, budgetAfterTopUp);

            state.setTopUpsTodayCount(state.getTopUpsTodayCount() + 1);
            SlotBudgetSpendUtils.addSlotTopUp(state, topUpAmount);
            stateRepository.save(state);

            String cashbackNote = attempt.request().getCashbackSum() != null && attempt.request().getCashbackSum() > 0
                    ? (", из них промо " + attempt.request().getCashbackSum() + " ₽ до "
                    + attempt.request().getCashbackPercent() + "%")
                    : "";
            String sourceNote = attempt.usedBackup()
                    ? ", источник: запасной (" + formatSourceLabel(attempt.sourceType()) + ")"
                    : ", источник: " + formatSourceLabel(attempt.sourceType());
            changeLogService.log(advertId, cabinetId, null,
                    "Бюджет пополнен автоматически на " + topUpAmount + " ₽ ("
                            + budgetBeforeTopUp + " ₽ -> " + budgetAfterTopUp + " ₽)"
                            + cashbackNote + sourceNote);
            timelineService.recordTopUp(advertId, cabinetId, topUpAmount, budgetAfterTopUp);
            stateRepository.save(state);

            log.info("Автопополнение advertId={}: зачислено {} ₽{}{}, остаток бюджета РК {}",
                    advertId, topUpAmount, cashbackNote, sourceNote, budgetAfterTopUp);
            return Optional.of(topUpAmount);
        } catch (Exception e) {
            log.warn("Автопополнение advertId={}: {}", advertId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Deposit с основного источника; при ошибке — один retry с запасным (если задан и отличается).
     * Каждый цикл снова начинается с основного (без «залипания» на запасном).
     */
    private DepositAttemptResult depositWithOptionalBackup(
            Cabinet cabinet,
            Long advertId,
            WbCampaignAutoBudgetSettings settings,
            int topUpAmount,
            int primarySourceType,
            Integer backupSourceType
    ) {
        try {
            return depositOnce(cabinet, advertId, settings, topUpAmount, primarySourceType, false);
        } catch (Exception primaryError) {
            boolean canUseBackup = backupSourceType != null && !backupSourceType.equals(primarySourceType);
            if (!canUseBackup) {
                throw primaryError;
            }
            log.warn(
                    "Автопополнение advertId={}: основной источник {} недоступен ({}), пробуем запасной {}",
                    advertId,
                    formatSourceLabel(primarySourceType),
                    primaryError.getMessage(),
                    formatSourceLabel(backupSourceType)
            );
            return depositOnce(cabinet, advertId, settings, topUpAmount, backupSourceType, true);
        }
    }

    private DepositAttemptResult depositOnce(
            Cabinet cabinet,
            Long advertId,
            WbCampaignAutoBudgetSettings settings,
            int topUpAmount,
            int sourceType,
            boolean usedBackup
    ) {
        WbPromotionBudgetDepositRequest req = WbPromotionBudgetDepositRequest.builder()
                .sum(topUpAmount)
                .type(sourceType)
                .returnBudget(true)
                .build();
        WbCampaignBudgetDepositResult depositResult = budgetDepositService.depositWithPromoFallback(
                cabinet, advertId, req, settings.isUsePromoCashback(), true);
        return new DepositAttemptResult(req, depositResult, sourceType, usedBackup);
    }

    private static String formatSourceLabel(int sourceType) {
        return switch (sourceType) {
            case 0 -> "Счёт";
            case 1 -> "Баланс";
            case 3 -> "Бонусы";
            default -> "тип " + sourceType;
        };
    }

    private record DepositAttemptResult(
            WbPromotionBudgetDepositRequest request,
            WbCampaignBudgetDepositResult depositResult,
            int sourceType,
            boolean usedBackup
    ) {
    }

    private void resetTopUpCounterIfNewDay(WbCampaignManagementState state, LocalDate today) {
        if (state.getTopUpsTodayDate() == null || !state.getTopUpsTodayDate().equals(today)) {
            state.setTopUpsTodayDate(today);
            state.setTopUpsTodayCount(0);
        }
    }
}
