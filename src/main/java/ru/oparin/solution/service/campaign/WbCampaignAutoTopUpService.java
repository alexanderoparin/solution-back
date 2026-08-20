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
import ru.oparin.solution.service.wb.WbPromotionApiClient;

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
    private final WbPromotionApiClient promotionApiClient;
    private final WbCampaignBudgetFetchService budgetFetchService;
    private final WbCampaignChangeLogService changeLogService;
    private final WbCampaignBudgetTimelineService timelineService;
    private final WbCampaignStartBudgetGuard startBudgetGuard;

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
        try {
            WbPromotionBudgetDepositRequest req = WbPromotionBudgetDepositRequest.builder()
                    .sum(topUpAmount)
                    .type(settings.getSourceType() != null ? settings.getSourceType() : 1)
                    .returnBudget(true)
                    .build();
            // HTTP deposit вне длинной TX.
            WbPromotionBudgetResponse depositResponse = promotionApiClient.depositCampaignBudget(
                    cabinet.getApiKey(), advertId, req);
            int budgetAfterTopUp = budgetFetchService.resolveBudgetAfterTopUp(
                    budgetBeforeTopUp, topUpAmount, depositResponse);
            budgetFetchService.storeBudgetTotal(state, advertId, cabinetId, budgetAfterTopUp);
            startBudgetGuard.clearBlockIfBudgetAvailable(state, budgetAfterTopUp);

            state.setTopUpsTodayCount(state.getTopUpsTodayCount() + 1);
            SlotBudgetSpendUtils.addSlotTopUp(state, topUpAmount);
            stateRepository.save(state);

            changeLogService.log(advertId, cabinetId, null,
                    "Бюджет пополнен автоматически на " + topUpAmount + " ₽ ("
                            + budgetBeforeTopUp + " ₽ -> " + budgetAfterTopUp + " ₽)");
            timelineService.recordTopUp(advertId, cabinetId, topUpAmount, budgetAfterTopUp);
            stateRepository.save(state);

            log.info("Автопополнение advertId={}: зачислено {} ₽, остаток бюджета РК {}",
                    advertId, topUpAmount, budgetAfterTopUp);
            return Optional.of(topUpAmount);
        } catch (Exception e) {
            log.warn("Автопополнение advertId={}: {}", advertId, e.getMessage());
            return Optional.empty();
        }
    }

    private void resetTopUpCounterIfNewDay(WbCampaignManagementState state, LocalDate today) {
        if (state.getTopUpsTodayDate() == null || !state.getTopUpsTodayDate().equals(today)) {
            state.setTopUpsTodayDate(today);
            state.setTopUpsTodayCount(0);
        }
    }
}
