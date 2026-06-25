package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.PromotionControlCapabilitiesDto;
import ru.oparin.solution.dto.wb.PromotionBudgetDepositRequest;
import ru.oparin.solution.dto.wb.PromotionBudgetResponse;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.CampaignAutoBudgetSettings;
import ru.oparin.solution.model.CampaignManagementState;
import ru.oparin.solution.repository.CampaignAutoBudgetSettingsRepository;
import ru.oparin.solution.repository.CampaignManagementStateRepository;
import ru.oparin.solution.service.PromotionCampaignControlWriteService;
import ru.oparin.solution.service.wb.WbPromotionApiClient;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Автопополнение бюджета РК в отдельной транзакции: после успешного deposit учёт фиксируется
 * независимо от ошибок start/pause в основном тике планировщика.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignAutoTopUpService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");

    private final CampaignAutoBudgetSettingsRepository autoBudgetRepository;
    private final CampaignManagementStateRepository stateRepository;
    private final PromotionCampaignControlWriteService promotionControlWriteService;
    private final WbPromotionApiClient promotionApiClient;
    private final CampaignBudgetFetchService budgetFetchService;
    private final CampaignChangeLogService changeLogService;
    private final CampaignBudgetTimelineService timelineService;

    /**
     * Пополняет бюджет при необходимости и сохраняет учёт (журнал, timeline, состояние слота).
     *
     * @return сумма пополнения в рублях, если deposit выполнен
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

        Optional<CampaignAutoBudgetSettings> settingsOpt = autoBudgetRepository.findById(advertId);
        if (settingsOpt.isEmpty()) {
            return Optional.empty();
        }
        CampaignAutoBudgetSettings settings = settingsOpt.get();
        if (!settings.isEnabled() || settings.getTopUpAmount() == null || settings.getThresholdRub() == null) {
            return Optional.empty();
        }

        CampaignManagementState state = stateRepository.findById(advertId).orElse(null);
        if (state == null) {
            return Optional.empty();
        }

        LocalDate today = LocalDate.now(ZONE);
        resetTopUpCounterIfNewDay(state, today);
        if (settings.getMaxTopUpsPerDay() != null && state.getTopUpsTodayCount() >= settings.getMaxTopUpsPerDay()) {
            return Optional.empty();
        }

        Optional<Integer> budgetTotal = budgetFetchService.fetchBudgetTotal(cabinet, advertId, state);
        if (budgetTotal.isEmpty() || budgetTotal.get() >= settings.getThresholdRub()) {
            stateRepository.save(state);
            return Optional.empty();
        }

        int budgetBeforeTopUp = budgetTotal.get();
        int topUpAmount = settings.getTopUpAmount();
        try {
            PromotionBudgetDepositRequest req = PromotionBudgetDepositRequest.builder()
                    .sum(topUpAmount)
                    .type(settings.getSourceType() != null ? settings.getSourceType() : 1)
                    .returnBudget(true)
                    .build();
            PromotionBudgetResponse depositResponse = promotionApiClient.depositCampaignBudget(
                    cabinet.getApiKey(), advertId, req);
            int budgetAfterTopUp = budgetFetchService.resolveBudgetAfterTopUp(
                    budgetBeforeTopUp, topUpAmount, depositResponse);
            budgetFetchService.storeBudgetTotal(state, advertId, cabinetId, budgetAfterTopUp);

            state.setTopUpsTodayCount(state.getTopUpsTodayCount() + 1);
            SlotBudgetSpendUtils.addSlotTopUp(state, topUpAmount);
            stateRepository.save(state);

            changeLogService.log(advertId, cabinetId, null,
                    "Бюджет пополнен автоматически на " + topUpAmount + " ₽");
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

    private void resetTopUpCounterIfNewDay(CampaignManagementState state, LocalDate today) {
        if (state.getTopUpsTodayDate() == null || !state.getTopUpsTodayDate().equals(today)) {
            state.setTopUpsTodayDate(today);
            state.setTopUpsTodayCount(0);
        }
    }
}
