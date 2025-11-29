package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.wb.PromotionFullStatsResponse;
import ru.oparin.solution.model.PromotionCampaign;
import ru.oparin.solution.model.PromotionCampaignStatistics;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.PromotionCampaignRepository;
import ru.oparin.solution.repository.PromotionCampaignStatisticsRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Сервис для работы со статистикой рекламных кампаний.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PromotionCampaignStatisticsService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final PromotionCampaignStatisticsRepository statisticsRepository;
    private final PromotionCampaignRepository campaignRepository;

    /**
     * Получает список дат, за которые уже есть статистика для кампании.
     *
     * @param campaignId ID кампании
     * @param dateFrom дата начала периода
     * @param dateTo дата окончания периода
     * @return список дат, за которые есть статистика
     */
    public List<LocalDate> getExistingStatisticsDates(Long campaignId, LocalDate dateFrom, LocalDate dateTo) {
        return statisticsRepository.findByCampaignAdvertIdAndDateBetween(campaignId, dateFrom, dateTo)
                .stream()
                .map(PromotionCampaignStatistics::getDate)
                .collect(Collectors.toList());
    }

    /**
     * Сохраняет или обновляет статистику кампаний из ответа WB API.
     *
     * @param response ответ от WB API со статистикой
     * @param seller   продавец, владелец кампаний
     */
    @Transactional
    public void saveOrUpdateStatistics(PromotionFullStatsResponse response, User seller) {
        if (isEmptyResponse(response)) {
            log.info("Ответ со статистикой пуст, сохранение/обновление не требуется.");
            return;
        }

        ProcessingResult result = processStatistics(response.getAdverts(), seller);

        log.info("Обработано записей статистики: создано {}, обновлено {}, пропущено {}",
                result.savedCount(), result.updatedCount(), result.skippedCount());
    }

    private boolean isEmptyResponse(PromotionFullStatsResponse response) {
        return response == null
                || response.getAdverts() == null
                || response.getAdverts().isEmpty();
    }

    private ProcessingResult processStatistics(
            List<PromotionFullStatsResponse.CampaignStats> statsDtos,
            User seller
    ) {
        int savedCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;

        for (PromotionFullStatsResponse.CampaignStats campaignStats : statsDtos) {
            if (!isValidCampaignStats(campaignStats)) {
                skippedCount++;
                continue;
            }

            // Разворачиваем данные из массива days в отдельные записи статистики
            if (campaignStats.getDays() == null || campaignStats.getDays().isEmpty()) {
                log.debug("У кампании advertId {} нет данных по дням, пропускаем", campaignStats.getAdvertId());
                skippedCount++;
                continue;
            }

            for (PromotionFullStatsResponse.CampaignStats.DayStats dayStats : campaignStats.getDays()) {
                try {
                    Optional<SaveResult> result = processDayStats(campaignStats.getAdvertId(), dayStats, seller);
                    if (result.isPresent()) {
                        if (result.get().isNew()) {
                            savedCount++;
                        } else {
                            updatedCount++;
                        }
                    } else {
                        skippedCount++;
                    }
                } catch (Exception e) {
                    log.error("Ошибка при обработке статистики для кампании advertId {} за дату {}: {}",
                            campaignStats.getAdvertId(), dayStats.getDate(), e.getMessage(), e);
                    skippedCount++;
                }
            }
        }

        return new ProcessingResult(savedCount, updatedCount, skippedCount);
    }

    private boolean isValidCampaignStats(PromotionFullStatsResponse.CampaignStats statsDto) {
        if (statsDto == null || statsDto.getAdvertId() == null) {
            log.warn("Получена некорректная DTO статистики кампании (null или advertId null), пропускаем.");
            return false;
        }
        return true;
    }

    private Optional<SaveResult> processDayStats(
            Long advertId,
            PromotionFullStatsResponse.CampaignStats.DayStats dayStats,
            User seller
    ) {
        if (!isValidDayStats(dayStats)) {
            return Optional.empty();
        }

        Optional<PromotionCampaign> campaignOptional = findCampaign(advertId, seller.getId());
        if (campaignOptional.isEmpty()) {
            return Optional.empty();
        }

        LocalDate date = parseDate(dayStats.getDate());
        if (date == null) {
            log.warn("Не удалось распарсить дату '{}' для кампании advertId {}, пропускаем",
                    dayStats.getDate(), advertId);
            return Optional.empty();
        }

        PromotionCampaign campaign = campaignOptional.get();
        PromotionCampaignStatistics statistics = findOrCreateStatistics(campaign, date);
        boolean isNew = statistics.getId() == null;

        updateStatisticsFields(statistics, dayStats);
        statisticsRepository.save(statistics);
        statisticsRepository.flush();

        return Optional.of(new SaveResult(isNew));
    }

    private boolean isValidDayStats(PromotionFullStatsResponse.CampaignStats.DayStats dayStats) {
        if (dayStats == null || dayStats.getDate() == null) {
            log.warn("Получена некорректная DTO статистики дня (null или date null), пропускаем.");
            return false;
        }
        return true;
    }

    private Optional<PromotionCampaign> findCampaign(Long advertId, Long sellerId) {
        Optional<PromotionCampaign> campaignOptional = campaignRepository.findByAdvertIdAndSellerId(
                advertId,
                sellerId
        );

        if (campaignOptional.isEmpty()) {
            log.warn("Кампания advertId {} не найдена для продавца (ID: {}), пропускаем статистику",
                    advertId, sellerId);
        }

        return campaignOptional;
    }

    private PromotionCampaignStatistics findOrCreateStatistics(PromotionCampaign campaign, LocalDate date) {
        Optional<PromotionCampaignStatistics> existingStats = statisticsRepository.findByCampaignAdvertIdAndDate(
                campaign.getAdvertId(),
                date
        );

        if (existingStats.isPresent()) {
            return existingStats.get();
        }

        PromotionCampaignStatistics newStatistics = new PromotionCampaignStatistics();
        newStatistics.setCampaign(campaign);
        newStatistics.setDate(date);
        return newStatistics;
    }

    /**
     * Парсит дату из формата ISO 8601 (например, "2025-11-23T00:00:00Z") в LocalDate.
     */
    private LocalDate parseDate(String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return null;
        }
        try {
            // Формат ISO 8601: "2025-11-23T00:00:00Z"
            // Извлекаем только дату (первые 10 символов)
            if (dateString.length() >= 10) {
                return LocalDate.parse(dateString.substring(0, 10), DATE_FORMATTER);
            }
            return LocalDate.parse(dateString, DATE_FORMATTER);
        } catch (Exception e) {
            log.warn("Не удалось распарсить дату '{}': {}", dateString, e.getMessage());
            return null;
        }
    }

    private void updateStatisticsFields(
            PromotionCampaignStatistics statistics,
            PromotionFullStatsResponse.CampaignStats.DayStats dayStats
    ) {
        statistics.setViews(dayStats.getViews());
        statistics.setClicks(dayStats.getClicks());
        statistics.setCtr(dayStats.getCtr());
        // sum в API приходит в рублях (BigDecimal), конвертируем в копейки (Long)
        statistics.setSum(convertRublesToKopecks(dayStats.getSum()));
        statistics.setOrders(dayStats.getOrders());
        statistics.setCr(dayStats.getCr());
        // CPA вычисляем из данных или используем cpc
        statistics.setCpa(dayStats.getCpc());
        // sum_price в API приходит в рублях (BigDecimal), конвертируем в копейки (Long)
        statistics.setOrdersSum(convertRublesToKopecks(dayStats.getSumPrice()));
    }

    /**
     * Конвертирует рубли (BigDecimal) в копейки (Long).
     */
    private Long convertRublesToKopecks(BigDecimal rubles) {
        if (rubles == null) {
            return null;
        }
        return rubles.multiply(BigDecimal.valueOf(100)).longValue();
    }

    private record ProcessingResult(int savedCount, int updatedCount, int skippedCount) {
    }

    private record SaveResult(boolean isNew) {
    }
}

