package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.wb.WbPromotionFullStatsV4Response;
import ru.oparin.solution.model.User;
import ru.oparin.solution.model.WbPromotionCampaign;
import ru.oparin.solution.model.WbPromotionCampaignStatistics;
import ru.oparin.solution.model.WbPromotionStatsSlice;
import ru.oparin.solution.repository.WbPromotionCampaignRepository;
import ru.oparin.solution.repository.WbPromotionCampaignStatisticsRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Сервис статистики рекламных кампаний WB (срезы placement / conversion_type).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WbPromotionCampaignStatisticsService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int CPA_SCALE = 2;
    private static final int MIN_DATE_STRING_LENGTH = 10;

    private final WbPromotionCampaignStatisticsRepository statisticsRepository;
    private final WbPromotionCampaignRepository campaignRepository;

    /**
     * Сохраняет или обновляет срезы статистики из ответа v4 fullstats.
     * Для затронутых (campaign, nm, date) удаляет исторический агрегат {@code all}/{@code all},
     * чтобы итог не дублировался.
     *
     * @param response ответ WB API
     * @param seller   продавец — владелец кампаний
     */
    @Transactional
    public void saveOrUpdateStatistics(WbPromotionFullStatsV4Response response, User seller) {
        if (response == null || response.getRows() == null || response.getRows().isEmpty()) {
            log.info("Ответ со статистикой v4 пуст, сохранение не требуется.");
            return;
        }

        int savedCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        Set<String> clearedLegacyKeys = new HashSet<>();

        for (WbPromotionFullStatsV4Response.Row row : response.getRows()) {
            try {
                Optional<SaveResult> result = processRow(row, seller, clearedLegacyKeys);
                if (result.isEmpty()) {
                    skippedCount++;
                } else if (result.get().isNew()) {
                    savedCount++;
                } else {
                    updatedCount++;
                }
            } catch (Exception e) {
                log.error("Ошибка при обработке строки fullstats v4 advertId={} nmId={} date={}: {}",
                        row != null ? row.getAdvertId() : null,
                        row != null ? row.getNmId() : null,
                        row != null ? row.getDate() : null,
                        e.getMessage(), e);
                skippedCount++;
            }
        }

        log.info("Обработано срезов статистики v4: создано {}, обновлено {}, пропущено {}",
                savedCount, updatedCount, skippedCount);
    }

    private Optional<SaveResult> processRow(
            WbPromotionFullStatsV4Response.Row row,
            User seller,
            Set<String> clearedLegacyKeys
    ) {
        if (row == null || row.getAdvertId() == null || row.getNmId() == null) {
            return Optional.empty();
        }
        String placement = normalizePlacement(row.getPlacement());
        String conversionType = normalizeConversionType(row.getConversionType());
        if (placement == null || conversionType == null) {
            log.warn("Пропуск строки fullstats v4: неизвестный placement={} или conversionType={}",
                    row.getPlacement(), row.getConversionType());
            return Optional.empty();
        }

        LocalDate date = parseDate(row.getDate());
        if (date == null) {
            return Optional.empty();
        }

        Optional<WbPromotionCampaign> campaignOptional = findCampaign(row.getAdvertId(), seller.getId());
        if (campaignOptional.isEmpty()) {
            return Optional.empty();
        }
        WbPromotionCampaign campaign = campaignOptional.get();

        clearLegacyAggregateIfNeeded(campaign.getAdvertId(), row.getNmId(), date, clearedLegacyKeys);

        WbPromotionCampaignStatistics statistics = findOrCreateStatistics(
                campaign, row.getNmId(), date, placement, conversionType);
        boolean isNew = statistics.getId() == null;
        updateStatisticsFields(statistics, row);
        statisticsRepository.save(statistics);
        return Optional.of(new SaveResult(isNew));
    }

    /**
     * Удаляет агрегат all/all один раз на ключ (campaign, nm, date) в рамках батча.
     */
    private void clearLegacyAggregateIfNeeded(
            Long advertId,
            Long nmId,
            LocalDate date,
            Set<String> clearedLegacyKeys
    ) {
        String key = advertId + ":" + nmId + ":" + date;
        if (!clearedLegacyKeys.add(key)) {
            return;
        }
        statisticsRepository.deleteByCampaign_AdvertIdAndNmIdAndDateAndPlacementAndConversionType(
                advertId,
                nmId,
                date,
                WbPromotionStatsSlice.ALL,
                WbPromotionStatsSlice.ALL
        );
    }

    private Optional<WbPromotionCampaign> findCampaign(Long advertId, Long sellerId) {
        Optional<WbPromotionCampaign> campaignOptional = campaignRepository.findById(advertId);
        if (campaignOptional.isEmpty()) {
            log.warn("Кампания advertId {} не найдена в БД, пропускаем статистику", advertId);
            return Optional.empty();
        }
        WbPromotionCampaign campaign = campaignOptional.get();
        if (campaign.getCabinet() == null
                || campaign.getCabinet().getUser() == null
                || !campaign.getCabinet().getUser().getId().equals(sellerId)) {
            log.warn("Кампания advertId {} не принадлежит продавцу (ID: {}), пропускаем статистику",
                    advertId, sellerId);
            return Optional.empty();
        }
        return campaignOptional;
    }

    private WbPromotionCampaignStatistics findOrCreateStatistics(
            WbPromotionCampaign campaign,
            Long nmId,
            LocalDate date,
            String placement,
            String conversionType
    ) {
        Optional<WbPromotionCampaignStatistics> existing = statisticsRepository
                .findByCampaignAdvertIdAndNmIdAndDateAndPlacementAndConversionType(
                        campaign.getAdvertId(), nmId, date, placement, conversionType);
        if (existing.isPresent()) {
            return existing.get();
        }
        WbPromotionCampaignStatistics created = new WbPromotionCampaignStatistics();
        created.setCampaign(campaign);
        created.setNmId(nmId);
        created.setDate(date);
        created.setPlacement(placement);
        created.setConversionType(conversionType);
        return created;
    }

    private void updateStatisticsFields(
            WbPromotionCampaignStatistics statistics,
            WbPromotionFullStatsV4Response.Row row
    ) {
        Integer views = toInt(row.getViews());
        Integer clicks = toInt(row.getClicks());
        Integer orders = row.getOrders();
        BigDecimal sum = row.getSum();

        statistics.setViews(views);
        statistics.setClicks(clicks);
        statistics.setOrders(orders);
        statistics.setAtbs(row.getAtbs());
        statistics.setCanceled(row.getCanceled());
        statistics.setShks(row.getShks());
        statistics.setSum(sum);
        statistics.setSumPrice(row.getSumPrice());
        statistics.setOrdersSum(row.getSumPrice());
        statistics.setCtr(row.getCtr());
        statistics.setCr(row.getCr());
        statistics.setCpc(row.getCpc());
        statistics.setCpa(calculateCpa(sum, orders));
        statistics.setAvgPos(row.getAvgPos());
    }

    private BigDecimal calculateCpa(BigDecimal sum, Integer orders) {
        if (sum == null || orders == null || orders == 0) {
            return null;
        }
        return sum.divide(BigDecimal.valueOf(orders), CPA_SCALE, RoundingMode.HALF_UP);
    }

    private static Integer toInt(Long value) {
        if (value == null) {
            return null;
        }
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return value.intValue();
    }

    private static String normalizePlacement(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toLowerCase();
        if (WbPromotionStatsSlice.PLACEMENT_SEARCH.equals(value)) {
            return WbPromotionStatsSlice.PLACEMENT_SEARCH;
        }
        if (WbPromotionStatsSlice.PLACEMENT_RECOMMENDATION.equals(value)
                || "recommendations".equals(value)) {
            return WbPromotionStatsSlice.PLACEMENT_RECOMMENDATION;
        }
        return null;
    }

    private static String normalizeConversionType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toLowerCase();
        if (WbPromotionStatsSlice.CONVERSION_DIRECT.equals(value)
                || WbPromotionStatsSlice.CONVERSION_MULTICARD.equals(value)
                || WbPromotionStatsSlice.CONVERSION_ASSOCIATED.equals(value)) {
            return value;
        }
        return null;
    }

    private LocalDate parseDate(String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return null;
        }
        try {
            String datePart = dateString.length() >= MIN_DATE_STRING_LENGTH
                    ? dateString.substring(0, MIN_DATE_STRING_LENGTH)
                    : dateString;
            return LocalDate.parse(datePart, DATE_FORMATTER);
        } catch (Exception e) {
            log.warn("Не удалось распарсить дату '{}': {}", dateString, e.getMessage());
            return null;
        }
    }

    private record SaveResult(boolean isNew) {
    }
}
