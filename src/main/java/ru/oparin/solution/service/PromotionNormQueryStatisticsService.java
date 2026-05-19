package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.NormQueryClusterRowDto;
import ru.oparin.solution.dto.analytics.NormQueryClusterSortField;
import ru.oparin.solution.dto.analytics.NormQueryClustersResponseDto;
import ru.oparin.solution.dto.wb.NormQueryStatsResponse;
import ru.oparin.solution.model.PromotionCampaign;
import ru.oparin.solution.model.PromotionNormQueryStatistics;
import ru.oparin.solution.repository.PromotionCampaignRepository;
import ru.oparin.solution.repository.PromotionNormQueryStatisticsRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Сохранение и чтение статистики по поисковым кластерам WB.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PromotionNormQueryStatisticsService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int MIN_DATE_STRING_LENGTH = 10;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final PromotionNormQueryStatisticsRepository repository;
    private final PromotionCampaignRepository campaignRepository;

    /**
     * Удаляет данные за период по кампаниям батча и сохраняет ответ WB.
     */
    @Transactional
    public void replaceStatisticsForCampaigns(
            NormQueryStatsResponse response,
            List<Long> campaignIds,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        if (campaignIds == null || campaignIds.isEmpty()) {
            return;
        }
        repository.deleteByCampaignAdvertIdInAndDateBetween(campaignIds, dateFrom, dateTo);
        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            log.info("Ответ normquery stats пуст для кампаний {}", campaignIds);
            return;
        }
        List<PromotionNormQueryStatistics> toSave = new ArrayList<>();
        for (NormQueryStatsResponse.ResponseItem item : response.getItems()) {
            if (item.getAdvertId() == null || item.getNmId() == null || item.getDailyStats() == null) {
                continue;
            }
            Optional<PromotionCampaign> campaignOpt = campaignRepository.findById(item.getAdvertId());
            if (campaignOpt.isEmpty()) {
                log.debug("Кампания advertId {} не найдена, пропуск normquery", item.getAdvertId());
                continue;
            }
            PromotionCampaign campaign = campaignOpt.get();
            for (NormQueryStatsResponse.DailyStat daily : item.getDailyStats()) {
                if (daily == null || daily.getStat() == null) {
                    continue;
                }
                LocalDate date = parseDate(daily.getDate());
                if (date == null) {
                    continue;
                }
                NormQueryStatsResponse.Stat stat = daily.getStat();
                if (stat.getNormQuery() == null || stat.getNormQuery().isBlank()) {
                    continue;
                }
                toSave.add(PromotionNormQueryStatistics.builder()
                        .campaign(campaign)
                        .nmId(item.getNmId())
                        .date(date)
                        .normQuery(stat.getNormQuery().trim())
                        .avgPos(stat.getAvgPos())
                        .clicks(stat.getClicks())
                        .atbs(stat.getAtbs())
                        .orders(stat.getOrders())
                        .shks(stat.getShks())
                        .spend(stat.getSpend())
                        .cpc(stat.getCpc())
                        .views(stat.getViews())
                        .ctr(stat.getCtr())
                        .cpm(stat.getCpm())
                        .build());
            }
        }
        if (!toSave.isEmpty()) {
            repository.saveAll(toSave);
            log.info("Сохранено {} записей normquery stats для кампаний {}", toSave.size(), campaignIds);
        }
    }

    /**
     * Агрегированные кластеры за период: постранично, с поиском и сортировкой.
     *
     * @param search подстрока для фильтра по {@code norm_query} (без учёта регистра)
     */
    @Transactional(readOnly = true)
    public NormQueryClustersResponseDto getAggregatedClustersPage(
            Long campaignId,
            LocalDate dateFrom,
            LocalDate dateTo,
            Long nmId,
            String search,
            NormQueryClusterSortField sortBy,
            Sort.Direction sortDir,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        String searchPattern = toSearchPattern(search);
        NormQueryClusterSortField resolvedSortBy = sortBy != null ? sortBy : NormQueryClusterSortField.CLICKS;
        Sort.Direction resolvedSortDir = sortDir != null ? sortDir : Sort.Direction.DESC;

        long totalElements = repository.countAggregatedClusters(
                campaignId, dateFrom, dateTo, nmId, searchPattern);
        List<PromotionNormQueryStatisticsRepository.NormQueryClusterAggregateRow> rows =
                repository.findAggregatedClustersPage(
                        campaignId,
                        dateFrom,
                        dateTo,
                        nmId,
                        searchPattern,
                        resolvedSortBy,
                        resolvedSortDir,
                        safeSize,
                        safePage * safeSize
                );
        LocalDateTime lastSyncedAt = repository.findMaxUpdatedAt(campaignId, dateFrom, dateTo, nmId);

        NormQueryClusterRowDto totals = null;
        if (safePage == 0) {
            PromotionNormQueryStatisticsRepository.NormQueryClusterTotalsRow totalsRow =
                    repository.findTotalsByCampaignAndPeriod(
                            campaignId, dateFrom, dateTo, nmId, searchPattern);
            totals = mapTotalsRow(totalsRow);
        }

        List<NormQueryClusterRowDto> rowDtos = rows.stream()
                .map(this::mapAggregateRow)
                .toList();
        boolean hasMore = (long) (safePage + 1) * safeSize < totalElements;

        return NormQueryClustersResponseDto.builder()
                .totals(totals)
                .rows(rowDtos)
                .totalElements(totalElements)
                .page(safePage)
                .size(safeSize)
                .hasMore(hasMore)
                .lastSyncedAt(lastSyncedAt)
                .build();
    }

    private static String toSearchPattern(String search) {
        if (search == null) {
            return null;
        }
        String trimmed = search.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return "%" + trimmed.toLowerCase() + "%";
    }

    private NormQueryClusterRowDto mapAggregateRow(
            PromotionNormQueryStatisticsRepository.NormQueryClusterAggregateRow row
    ) {
        return NormQueryClusterRowDto.builder()
                .normQuery(row.getNormQuery())
                .avgPos(row.getAvgPos())
                .clicks(row.getClicks())
                .atbs(row.getAtbs())
                .orders(row.getOrders())
                .spend(row.getSpend())
                .cpc(row.getCpc())
                .build();
    }

    private NormQueryClusterRowDto mapTotalsRow(
            PromotionNormQueryStatisticsRepository.NormQueryClusterTotalsRow row
    ) {
        if (row == null) {
            return emptyTotals();
        }
        return NormQueryClusterRowDto.builder()
                .normQuery(null)
                .avgPos(row.getAvgPos())
                .clicks(row.getClicks() != null ? row.getClicks() : 0)
                .atbs(row.getAtbs() != null ? row.getAtbs() : 0)
                .orders(row.getOrders() != null ? row.getOrders() : 0)
                .spend(row.getSpend())
                .cpc(row.getCpc())
                .build();
    }

    private static NormQueryClusterRowDto emptyTotals() {
        return NormQueryClusterRowDto.builder()
                .clicks(0)
                .atbs(0)
                .orders(0)
                .build();
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.length() < MIN_DATE_STRING_LENGTH) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr.substring(0, MIN_DATE_STRING_LENGTH), DATE_FORMATTER);
        } catch (Exception e) {
            log.warn("Не удалось распарсить дату normquery: {}", dateStr);
            return null;
        }
    }
}
