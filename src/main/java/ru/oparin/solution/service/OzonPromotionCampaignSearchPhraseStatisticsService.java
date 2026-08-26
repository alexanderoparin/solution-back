package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.NormQueryClusterRowDto;
import ru.oparin.solution.dto.analytics.NormQueryClusterSortField;
import ru.oparin.solution.dto.analytics.NormQueryClustersResponseDto;
import ru.oparin.solution.dto.ozon.OzonPerformanceSearchPhrasesResponse;
import ru.oparin.solution.model.OzonPromotionCampaign;
import ru.oparin.solution.model.OzonPromotionCampaignSearchPhraseStatistics;
import ru.oparin.solution.repository.OzonPromotionCampaignSearchPhraseStatisticsRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Сохранение и чтение поисковых запросов (кластеров) Ozon Performance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OzonPromotionCampaignSearchPhraseStatisticsService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final OzonPromotionCampaignSearchPhraseStatisticsRepository repository;

    /**
     * Заменяет статистику по кампании за период данными async-отчёта.
     * Дубликаты по {@code (date, search_phrase)} агрегируются (сумма метрик).
     *
     * @return число сохранённых строк
     */
    @Transactional
    public int replaceForCampaign(
            OzonPromotionCampaign campaign,
            LocalDate dateFrom,
            LocalDate dateTo,
            List<OzonPerformanceSearchPhrasesResponse.Row> rows
    ) {
        if (campaign == null || dateFrom == null || dateTo == null) {
            return 0;
        }
        repository.deleteByCampaignIdAndDateBetween(campaign.getCampaignId(), dateFrom, dateTo);
        if (rows == null || rows.isEmpty()) {
            log.info("Ozon search phrases: пустой отчёт для campaignId={}", campaign.getCampaignId());
            return 0;
        }

        Map<String, OzonPromotionCampaignSearchPhraseStatistics> mergedByKey = new LinkedHashMap<>();
        int skipped = 0;
        for (OzonPerformanceSearchPhrasesResponse.Row row : rows) {
            if (row == null || !row.isValidDataRow()) {
                skipped++;
                continue;
            }
            String phrase = row.getSearchPhrase().trim();
            String key = row.getDate() + "|" + phrase.toLowerCase();
            OzonPromotionCampaignSearchPhraseStatistics existing = mergedByKey.get(key);
            if (existing == null) {
                mergedByKey.put(key, OzonPromotionCampaignSearchPhraseStatistics.builder()
                        .campaign(campaign)
                        .sku(row.getSku())
                        .date(row.getDate())
                        .searchPhrase(phrase)
                        .avgPos(row.getAvgPos())
                        .views(row.getViews())
                        .clicks(row.getClicks())
                        .ctr(row.getCtr())
                        .toCart(row.getToCart())
                        .avgCpc(row.getAvgCpc())
                        .spend(row.getSpend())
                        .orders(row.getOrders())
                        .build());
            } else {
                mergeInto(existing, row);
            }
        }

        List<OzonPromotionCampaignSearchPhraseStatistics> toSave = new ArrayList<>(mergedByKey.values());
        if (!toSave.isEmpty()) {
            repository.saveAll(toSave);
            log.info("Ozon search phrases: сохранено {} строк для campaignId={} (вход={}, пропущено={}, дублей={})",
                    toSave.size(),
                    campaign.getCampaignId(),
                    rows.size(),
                    skipped,
                    rows.size() - skipped - toSave.size());
        }
        return toSave.size();
    }

    private static void mergeInto(
            OzonPromotionCampaignSearchPhraseStatistics target,
            OzonPerformanceSearchPhrasesResponse.Row row
    ) {
        target.setViews(sumInt(target.getViews(), row.getViews()));
        target.setClicks(sumInt(target.getClicks(), row.getClicks()));
        target.setToCart(sumInt(target.getToCart(), row.getToCart()));
        target.setOrders(sumInt(target.getOrders(), row.getOrders()));
        target.setSpend(sumDecimal(target.getSpend(), row.getSpend()));
        if (target.getSku() == null && row.getSku() != null) {
            target.setSku(row.getSku());
        }
        target.setAvgPos(preferNonNull(target.getAvgPos(), row.getAvgPos()));
        Integer clicks = target.getClicks();
        Integer views = target.getViews();
        if (clicks != null && views != null && views > 0) {
            target.setCtr(BigDecimal.valueOf(clicks)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(views), 4, RoundingMode.HALF_UP));
        }
        BigDecimal spend = target.getSpend();
        if (spend != null && clicks != null && clicks > 0) {
            target.setAvgCpc(spend.divide(BigDecimal.valueOf(clicks), 4, RoundingMode.HALF_UP));
        }
    }

    private static Integer sumInt(Integer left, Integer right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left + right;
    }

    private static BigDecimal sumDecimal(BigDecimal left, BigDecimal right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.add(right);
    }

    private static BigDecimal preferNonNull(BigDecimal current, BigDecimal incoming) {
        return current != null ? current : incoming;
    }

    /**
     * Агрегированные кластеры за период: постранично, с поиском и сортировкой.
     *
     * @param sku фильтр по SKU (nmId на фронте); {@code null} — все фразы кампании
     */
    @Transactional(readOnly = true)
    public NormQueryClustersResponseDto getAggregatedClustersPage(
            Long campaignId,
            LocalDate dateFrom,
            LocalDate dateTo,
            Long sku,
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
                campaignId, dateFrom, dateTo, sku, searchPattern);
        List<OzonPromotionCampaignSearchPhraseStatisticsRepository.SearchPhraseClusterAggregateRow> rows =
                repository.findAggregatedClustersPage(
                        campaignId,
                        dateFrom,
                        dateTo,
                        sku,
                        searchPattern,
                        resolvedSortBy,
                        resolvedSortDir,
                        safeSize,
                        safePage * safeSize
                );
        LocalDateTime lastSyncedAt = repository.findMaxUpdatedAt(campaignId, dateFrom, dateTo, sku);

        NormQueryClusterRowDto totals = null;
        if (safePage == 0) {
            OzonPromotionCampaignSearchPhraseStatisticsRepository.SearchPhraseClusterTotalsRow totalsRow =
                    repository.findTotalsByCampaignAndPeriod(
                            campaignId, dateFrom, dateTo, sku, searchPattern);
            totals = mapTotalsRow(totalsRow);
        }

        List<NormQueryClusterRowDto> rowDtos = rows.stream()
                .map(this::mapAggregateRow)
                .toList();

        return NormQueryClustersResponseDto.builder()
                .totals(totals)
                .rows(rowDtos)
                .totalElements(totalElements)
                .page(safePage)
                .size(safeSize)
                .hasMore((long) (safePage + 1) * safeSize < totalElements)
                .lastSyncedAt(lastSyncedAt)
                .build();
    }

    private static String toSearchPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return "%" + search.trim().toLowerCase() + "%";
    }

    private NormQueryClusterRowDto mapAggregateRow(
            OzonPromotionCampaignSearchPhraseStatisticsRepository.SearchPhraseClusterAggregateRow row
    ) {
        return NormQueryClusterRowDto.builder()
                .normQuery(row.getSearchPhrase())
                .avgPos(row.getAvgPos())
                .clicks(row.getClicks())
                .atbs(row.getAtbs())
                .orders(row.getOrders())
                .spend(row.getSpend())
                .cpc(row.getCpc())
                .cpo(row.getCpo())
                .build();
    }

    private NormQueryClusterRowDto mapTotalsRow(
            OzonPromotionCampaignSearchPhraseStatisticsRepository.SearchPhraseClusterTotalsRow row
    ) {
        if (row == null) {
            return emptyTotals();
        }
        return NormQueryClusterRowDto.builder()
                .normQuery("Всего")
                .avgPos(row.getAvgPos())
                .clicks(row.getClicks())
                .atbs(row.getAtbs())
                .orders(row.getOrders())
                .spend(row.getSpend())
                .cpc(row.getCpc())
                .cpo(row.getCpo())
                .build();
    }

    private static NormQueryClusterRowDto emptyTotals() {
        return NormQueryClusterRowDto.builder()
                .normQuery("Всего")
                .build();
    }
}
