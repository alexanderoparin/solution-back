package ru.oparin.solution.service.abtest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.wb.PromotionFullStatsRequest;
import ru.oparin.solution.dto.wb.PromotionFullStatsResponse;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.AbTestCampaignRepository;
import ru.oparin.solution.repository.AbTestRepository;
import ru.oparin.solution.repository.AbTestStatsSnapshotRepository;
import ru.oparin.solution.repository.AbTestVariantRepository;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.wb.WbPromotionApiClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Сбор статистики А/Б-тестов: дельты fullstats → активный вариант.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AbTestStatsService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    private final AbTestRepository abTestRepository;
    private final AbTestCampaignRepository abTestCampaignRepository;
    private final AbTestVariantRepository abTestVariantRepository;
    private final AbTestStatsSnapshotRepository snapshotRepository;
    private final CabinetService cabinetService;
    private final WbPromotionApiClient promotionApiClient;
    private final AbTestService abTestService;

    /**
     * Обновить статистику по всем активным тестам кабинета / всем тестам.
     */
    @Transactional
    public void pollAllEnabled() {
        List<AbTest> tests = abTestRepository.findByStatus(AbTestStatus.ENABLED);
        for (AbTest test : tests) {
            try {
                pollOne(test);
            } catch (Exception e) {
                log.warn("Ошибка опроса статистики ab_test id={}: {}", test.getId(), e.getMessage());
            }
        }
    }

    /**
     * Опрос fullstats и атрибуция дельт активному варианту.
     */
    @Transactional
    public void pollOne(AbTest test) {
        Cabinet cabinet = cabinetService.findByIdWithUserOrThrow(test.getCabinetId());
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            return;
        }
        // Базовый токен: fullstats не чаще 1 раза в час — иначе 429.
        if (!CabinetTokenType.effective(cabinet.getTokenType()).supportsFrequentFullstats()) {
            if (test.getLastStatsAt() != null
                    && test.getLastStatsAt().isAfter(LocalDateTime.now().minusMinutes(55))) {
                return;
            }
        }
        List<AbTestCampaign> campaigns = abTestCampaignRepository.findByAbTestId(test.getId());
        if (campaigns.isEmpty() || test.getActiveVariantId() == null) {
            return;
        }
        List<Long> advertIds = campaigns.stream().map(AbTestCampaign::getAdvertId).toList();
        LocalDate from = test.getStartedAt() != null ? test.getStartedAt().toLocalDate() : LocalDate.now().minusDays(7);
        LocalDate to = LocalDate.now();

        PromotionFullStatsRequest request = PromotionFullStatsRequest.builder()
                .advertId(advertIds)
                .dateFrom(from.format(DAY))
                .dateTo(to.format(DAY))
                .build();

        PromotionFullStatsResponse response = promotionApiClient.getPromotionFullStats(cabinet.getApiKey(), request);
        Map<Long, MetricAgg> byAdvert = aggregateByAdvertAndNm(response, test.getNmId());

        AbTestVariant active = abTestVariantRepository.findById(test.getActiveVariantId()).orElse(null);
        if (active == null) {
            return;
        }

        long deltaViews = 0;
        long deltaClicks = 0;
        long deltaAtbs = 0;
        long deltaOrders = 0;
        boolean anyExistingSnapshot = false;

        for (AbTestCampaign link : campaigns) {
            MetricAgg current = byAdvert.getOrDefault(link.getAdvertId(), MetricAgg.ZERO);
            AbTestStatsSnapshot snapshot = snapshotRepository
                    .findByAbTestIdAndAdvertIdAndNmId(test.getId(), link.getAdvertId(), test.getNmId())
                    .orElse(null);

            boolean isAnchor = snapshot == null;
            if (!isAnchor) {
                anyExistingSnapshot = true;
                deltaViews += Math.max(0, current.views() - snapshot.getViews());
                deltaClicks += Math.max(0, current.clicks() - snapshot.getClicks());
                deltaAtbs += Math.max(0, current.atbs() - snapshot.getAtbs());
                deltaOrders += Math.max(0, current.orders() - snapshot.getOrders());
            }

            if (snapshot == null) {
                snapshot = AbTestStatsSnapshot.builder()
                        .abTestId(test.getId())
                        .advertId(link.getAdvertId())
                        .nmId(test.getNmId())
                        .build();
            }
            snapshot.setViews(current.views());
            snapshot.setClicks(current.clicks());
            snapshot.setAtbs(current.atbs());
            snapshot.setOrders(current.orders());
            snapshot.setCapturedAt(LocalDateTime.now());
            snapshotRepository.save(snapshot);
        }

        // Первый опрос после старта/новой РК — только якорь snapshot, без начисления
        // (иначе весь накопленный день уйдёт на первый активный вариант).
        if (anyExistingSnapshot
                && (deltaViews > 0 || deltaClicks > 0 || deltaAtbs > 0 || deltaOrders > 0)) {
            active.setViews(active.getViews() + deltaViews);
            active.setClicks(active.getClicks() + deltaClicks);
            active.setAtbs(active.getAtbs() + deltaAtbs);
            active.setOrders(active.getOrders() + deltaOrders);
            abTestVariantRepository.save(active);
        }

        test.setLastStatsAt(LocalDateTime.now());
        abTestRepository.save(test);
        abTestService.refreshInsight(test);
    }

    private Map<Long, MetricAgg> aggregateByAdvertAndNm(PromotionFullStatsResponse response, Long nmId) {
        Map<Long, MetricAgg> result = new HashMap<>();
        if (response == null || response.getAdverts() == null) {
            return result;
        }
        for (PromotionFullStatsResponse.CampaignStats campaign : response.getAdverts()) {
            if (campaign.getAdvertId() == null || campaign.getDays() == null) {
                continue;
            }
            int views = 0;
            int clicks = 0;
            int atbs = 0;
            int orders = 0;
            for (PromotionFullStatsResponse.CampaignStats.DayStats day : campaign.getDays()) {
                if (day.getApps() == null) {
                    continue;
                }
                for (var app : day.getApps()) {
                    if (app.getNms() == null) {
                        continue;
                    }
                    for (var nm : app.getNms()) {
                        if (nmId.equals(nm.getNmId())) {
                            views += nz(nm.getViews());
                            clicks += nz(nm.getClicks());
                            atbs += nz(nm.getAtbs());
                            orders += nz(nm.getOrders());
                        }
                    }
                }
            }
            result.put(campaign.getAdvertId(), new MetricAgg(views, clicks, atbs, orders));
        }
        return result;
    }

    private static int nz(Integer v) {
        return v != null ? v : 0;
    }

    private record MetricAgg(int views, int clicks, int atbs, int orders) {
        static final MetricAgg ZERO = new MetricAgg(0, 0, 0, 0);
    }
}
