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
import java.math.RoundingMode;
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
    private static final int CPA_SCALE = 2;
    private static final int MIN_DATE_STRING_LENGTH = 10;

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
                .distinct() // Убираем дубликаты дат (в кампании может быть несколько артикулов)
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
                    ProcessingCounters counters = processDayStats(campaignStats.getAdvertId(), dayStats, seller);
                    savedCount += counters.saved();
                    updatedCount += counters.updated();
                    skippedCount += counters.skipped();
                } catch (Exception e) {
                    log.error("Ошибка при обработке статистики для кампании advertId {} за дату {}: {}",
                            campaignStats.getAdvertId(), dayStats.getDate(), e.getMessage(), e);
                    skippedCount++;
                }
            }
        }

        return new ProcessingResult(savedCount, updatedCount, skippedCount);
    }

    /**
     * Обрабатывает статистику за один день, извлекая данные по всем артикулам.
     */
    private ProcessingCounters processDayStats(
            Long advertId,
            PromotionFullStatsResponse.CampaignStats.DayStats dayStats,
            User seller
    ) {
        int saved = 0;
        int updated = 0;
        int skipped = 0;

        if (dayStats.getApps() == null || dayStats.getApps().isEmpty()) {
            return new ProcessingCounters(saved, updated, skipped);
        }

        for (PromotionFullStatsResponse.CampaignStats.DayStats.AppStats appStats : dayStats.getApps()) {
            if (appStats.getNms() == null || appStats.getNms().isEmpty()) {
                continue;
            }

            for (PromotionFullStatsResponse.CampaignStats.DayStats.ArticleStats articleStats : appStats.getNms()) {
                Optional<SaveResult> result = processArticleStats(advertId, dayStats, articleStats, seller);
                if (result.isPresent()) {
                    if (result.get().isNew()) {
                        saved++;
                    } else {
                        updated++;
                    }
                } else {
                    skipped++;
                }
            }
        }

        return new ProcessingCounters(saved, updated, skipped);
    }

    private boolean isValidCampaignStats(PromotionFullStatsResponse.CampaignStats statsDto) {
        if (statsDto == null || statsDto.getAdvertId() == null) {
            log.warn("Получена некорректная DTO статистики кампании (null или advertId null), пропускаем.");
            return false;
        }
        return true;
    }

    private Optional<SaveResult> processArticleStats(
            Long advertId,
            PromotionFullStatsResponse.CampaignStats.DayStats dayStats,
            PromotionFullStatsResponse.CampaignStats.DayStats.ArticleStats articleStats,
            User seller
    ) {
        if (!isValidDayStats(dayStats)) {
            return Optional.empty();
        }

        if (articleStats == null || articleStats.getNmId() == null) {
            log.warn("Получена некорректная DTO статистики артикула (null или nmId null), пропускаем.");
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
        PromotionCampaignStatistics statistics = findOrCreateStatistics(campaign, articleStats.getNmId(), date);
        boolean isNew = statistics.getId() == null;

        updateStatisticsFields(statistics, articleStats);
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

    private PromotionCampaignStatistics findOrCreateStatistics(PromotionCampaign campaign, Long nmId, LocalDate date) {
        Optional<PromotionCampaignStatistics> existingStats = statisticsRepository.findByCampaignAdvertIdAndNmIdAndDate(
                campaign.getAdvertId(),
                nmId,
                date
        );

        if (existingStats.isPresent()) {
            return existingStats.get();
        }

        PromotionCampaignStatistics newStatistics = new PromotionCampaignStatistics();
        newStatistics.setCampaign(campaign);
        newStatistics.setNmId(nmId);
        newStatistics.setDate(date);
        return newStatistics;
    }

    /**
     * Парсит дату из формата ISO 8601 (например, "2025-11-23T00:00:00Z") в LocalDate.
     * Извлекает только дату (первые 10 символов) из строки формата ISO 8601.
     *
     * @param dateString строка с датой в формате ISO 8601
     * @return LocalDate или null, если парсинг не удался
     */
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

    /**
     * Обновляет поля статистики из данных артикула.
     */
    private void updateStatisticsFields(
            PromotionCampaignStatistics statistics,
            PromotionFullStatsResponse.CampaignStats.DayStats.ArticleStats articleStats
    ) {
        setBasicMetrics(statistics, articleStats);
        setFinancialMetrics(statistics, articleStats);
        setConversionMetrics(statistics, articleStats);
    }

    /**
     * Устанавливает базовые метрики (показы, клики, заказы и т.д.).
     */
    private void setBasicMetrics(
            PromotionCampaignStatistics statistics,
            PromotionFullStatsResponse.CampaignStats.DayStats.ArticleStats articleStats
    ) {
        statistics.setViews(articleStats.getViews());
        statistics.setClicks(articleStats.getClicks());
        statistics.setOrders(articleStats.getOrders());
        statistics.setAtbs(articleStats.getAtbs());
        statistics.setCanceled(articleStats.getCanceled());
        statistics.setShks(articleStats.getShks());
    }

    /**
     * Устанавливает финансовые метрики (расходы, суммы заказов).
     */
    private void setFinancialMetrics(
            PromotionCampaignStatistics statistics,
            PromotionFullStatsResponse.CampaignStats.DayStats.ArticleStats articleStats
    ) {
        statistics.setSum(articleStats.getSum());
        statistics.setSumPrice(articleStats.getSumPrice());
        statistics.setOrdersSum(articleStats.getSumPrice()); // Для совместимости
    }

    /**
     * Устанавливает метрики конверсии (CTR, CR, CPC, CPA).
     */
    private void setConversionMetrics(
            PromotionCampaignStatistics statistics,
            PromotionFullStatsResponse.CampaignStats.DayStats.ArticleStats articleStats
    ) {
        statistics.setCtr(articleStats.getCtr());
        statistics.setCr(articleStats.getCr());
        statistics.setCpc(articleStats.getCpc());
        statistics.setCpa(calculateCpa(articleStats));
    }

    /**
     * Вычисляет CPA (Cost Per Action) = расходы / количество заказов.
     *
     * @param articleStats статистика артикула
     * @return CPA в рублях или null, если заказов нет
     */
    private BigDecimal calculateCpa(PromotionFullStatsResponse.CampaignStats.DayStats.ArticleStats articleStats) {
        BigDecimal sum = articleStats.getSum();
        Integer orders = articleStats.getOrders();

        if (sum == null || orders == null || orders == 0) {
            return null;
        }

        return sum.divide(BigDecimal.valueOf(orders), CPA_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Результат обработки статистики за день.
     */
    private record ProcessingCounters(int saved, int updated, int skipped) {
    }

    /**
     * Результат обработки всех статистик.
     */
    private record ProcessingResult(int savedCount, int updatedCount, int skippedCount) {
    }

    /**
     * Результат сохранения одной записи статистики.
     */
    private record SaveResult(boolean isNew) {
    }
}

