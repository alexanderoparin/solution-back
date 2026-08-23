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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
        List<OzonPromotionCampaignSearchPhraseStatistics> toSave = new ArrayList<>();
        for (OzonPerformanceSearchPhrasesResponse.Row row : rows) {
            if (row.getSearchPhrase() == null || row.getSearchPhrase().isBlank() || row.getDate() == null) {
                continue;
            }
            if (row.isTotalRow()) {
                continue;
            }
            toSave.add(OzonPromotionCampaignSearchPhraseStatistics.builder()
                    .campaign(campaign)
                    .sku(row.getSku())
                    .date(row.getDate())
                    .searchPhrase(row.getSearchPhrase().trim())
                    .avgPos(row.getAvgPos())
                    .views(row.getViews())
                    .clicks(row.getClicks())
                    .ctr(row.getCtr())
                    .toCart(row.getToCart())
                    .avgCpc(row.getAvgCpc())
                    .spend(row.getSpend())
                    .orders(row.getOrders())
                    .build());
        }
        if (!toSave.isEmpty()) {
            repository.saveAll(toSave);
            log.info("Ozon search phrases: сохранено {} строк для campaignId={}",
                    toSave.size(), campaign.getCampaignId());
        }
        return toSave.size();
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
