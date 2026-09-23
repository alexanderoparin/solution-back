package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.WbCampaignManagementState;
import ru.oparin.solution.model.WbCampaignStatus;
import ru.oparin.solution.model.WbPromotionCampaign;
import ru.oparin.solution.repository.WbCampaignManagementStateRepository;
import ru.oparin.solution.repository.WbPromotionCampaignRepository;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.sync.WbPromotionCampaignSyncService;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Пакетная сверка статусов РК с WB ({@code GET /api/advert/v2/adverts}) для кандидатов кабинета.
 * HTTP выполняется вне длинной транзакции.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CabinetStatusReconcileExecutor {

    /** Лимит ID в GET /api/advert/v2/adverts. */
    private static final int ADVERTS_BATCH_SIZE = 50;

    private final CabinetService cabinetService;
    private final WbCampaignManagementStateRepository stateRepository;
    private final WbPromotionCampaignRepository campaignRepository;
    private final WbPromotionCampaignSyncService promotionCampaignSyncService;
    private final WbCampaignScheduleControlNotifier scheduleControlNotifier;

    @Lazy
    @Autowired
    private CabinetStatusReconcileExecutor self;

    /**
     * Сверяет статусы кандидатов кабинета с WB и фиксирует внешнюю паузу в журнале/timeline.
     */
    public void reconcileCabinet(Long cabinetId, List<Long> advertIds, ZonedDateTime now) {
        if (advertIds == null || advertIds.isEmpty()) {
            return;
        }
        ReconcileContext ctx = self.loadContext(cabinetId, advertIds);
        if (ctx == null) {
            return;
        }
        LocalDateTime checkedAt = now.toLocalDateTime();
        try {
            for (int from = 0; from < ctx.advertIds().size(); from += ADVERTS_BATCH_SIZE) {
                int to = Math.min(from + ADVERTS_BATCH_SIZE, ctx.advertIds().size());
                List<Long> batch = List.copyOf(ctx.advertIds().subList(from, to));
                promotionCampaignSyncService.loadAndSaveAdvertsBatch(
                        ctx.cabinet(), ctx.cabinet().getApiKey(), batch);
            }
            self.applyReconcileResult(ctx, checkedAt);
        } catch (Exception e) {
            log.warn("Не удалось сверить статусы РК cabinetId={}: {}", cabinetId, e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ReconcileContext loadContext(Long cabinetId, List<Long> advertIds) {
        Cabinet cabinet = cabinetService.findById(cabinetId).orElse(null);
        if (cabinet == null || cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            return null;
        }
        List<Long> resolvedIds = new ArrayList<>();
        Map<Long, WbCampaignStatus> statusBefore = new HashMap<>();
        for (Long advertId : advertIds) {
            WbCampaignManagementState state = stateRepository.findById(advertId).orElse(null);
            if (state == null) {
                continue;
            }
            WbPromotionCampaign campaign = campaignRepository
                    .findByAdvertIdAndCabinet_Id(advertId, cabinetId)
                    .orElse(null);
            if (campaign == null || campaign.getStatus() != WbCampaignStatus.ACTIVE) {
                continue;
            }
            resolvedIds.add(advertId);
            statusBefore.put(advertId, campaign.getStatus());
        }
        if (resolvedIds.isEmpty()) {
            return null;
        }
        return new ReconcileContext(cabinet, resolvedIds, statusBefore);
    }

    /**
     * Обновляет метку сверки и пишет STOP, если WB уже не ACTIVE.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyReconcileResult(ReconcileContext ctx, LocalDateTime checkedAt) {
        Long cabinetId = ctx.cabinet().getId();
        List<WbCampaignManagementState> toSave = new ArrayList<>();
        for (Long advertId : ctx.advertIds()) {
            WbCampaignManagementState state = stateRepository.findById(advertId).orElse(null);
            if (state == null) {
                continue;
            }
            state.setLastStatusCheckedAt(checkedAt);
            toSave.add(state);

            if (ctx.statusBefore().get(advertId) != WbCampaignStatus.ACTIVE) {
                continue;
            }
            WbCampaignStatus after = campaignRepository
                    .findByAdvertIdAndCabinet_Id(advertId, cabinetId)
                    .map(WbPromotionCampaign::getStatus)
                    .orElse(null);
            if (after != null && after != WbCampaignStatus.ACTIVE) {
                scheduleControlNotifier.onPausedDetectedOnWb(advertId, cabinetId, after);
            }
        }
        if (!toSave.isEmpty()) {
            stateRepository.saveAll(toSave);
        }
    }

    /**
     * Контекст сверки после короткой read-only транзакции.
     */
    public record ReconcileContext(
            Cabinet cabinet,
            List<Long> advertIds,
            Map<Long, WbCampaignStatus> statusBefore
    ) {
    }
}
