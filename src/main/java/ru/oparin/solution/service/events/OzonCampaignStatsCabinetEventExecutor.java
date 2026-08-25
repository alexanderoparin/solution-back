package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.OzonApiEvent;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.events.payload.OzonCampaignStatsCabinetPayload;
import ru.oparin.solution.service.sync.OzonProductStatsSyncResult;
import ru.oparin.solution.service.sync.OzonPromotionCampaignSyncService;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Обновление списка РК, дневной статистики и async product-stats Ozon Performance за период.
 */
@Component("ozonCampaignStatsCabinetEventExecutor")
@RequiredArgsConstructor
@Slf4j
public class OzonCampaignStatsCabinetEventExecutor implements OzonApiEventExecutor {

    private final OzonApiEventService eventService;
    private final CabinetService cabinetService;
    private final OzonPromotionCampaignSyncService campaignSyncService;

    @Override
    public OzonApiEventExecutionResult execute(OzonApiEvent event) {
        OzonCampaignStatsCabinetPayload payload = eventService.readPayload(event, OzonCampaignStatsCabinetPayload.class);
        Cabinet cabinet = cabinetService.findByIdWithUserOrThrow(event.getCabinet().getId());
        String clientId = cabinet.getOzonPerformanceClientId();
        String clientSecret = cabinet.getOzonPerformanceClientSecret();
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            return OzonApiEventExecutionResult.finalError(
                    "У Ozon-кабинета не заданы Performance client_id или client_secret");
        }

        LocalDate dateTo = payload != null && payload.dateTo() != null
                ? payload.dateTo()
                : LocalDate.now().minusDays(1);
        LocalDate dateFrom = payload != null && payload.dateFrom() != null
                ? payload.dateFrom()
                : dateTo.minusDays(13);
        if (dateFrom.isAfter(dateTo)) {
            LocalDate tmp = dateFrom;
            dateFrom = dateTo;
            dateTo = tmp;
        }

        try {
            int statsRows = 0;
            boolean productStatsDone = payload != null && (
                    payload.isProductStatsDone()
                    || (payload.searchPhrasesReportUuid() != null
                            && !payload.searchPhrasesReportUuid().isBlank()
                            && (payload.productStatsReportUuid() == null
                                    || payload.productStatsReportUuid().isBlank()))
            );
            boolean resumeAsyncPhase = payload != null && (
                    (!productStatsDone
                            && payload.productStatsReportUuid() != null
                            && !payload.productStatsReportUuid().isBlank())
                    || (payload.searchPhrasesReportUuid() != null
                            && !payload.searchPhrasesReportUuid().isBlank())
                    || (!productStatsDone && payload.resolveProductStatsBatchStart() > 0)
                    || payload.resolveSearchPhrasesBatchStart() > 0
            );
            if (!resumeAsyncPhase) {
                statsRows = campaignSyncService.syncCampaignsAndDailyStats(
                        cabinet, clientId.trim(), clientSecret.trim(), dateFrom, dateTo);
            }

            int batchStart = payload != null ? payload.resolveProductStatsBatchStart() : 0;
            String reportUuid = payload != null ? payload.productStatsReportUuid() : null;
            int productRowsTotal = 0;
            int batchSize = campaignSyncService.getProductStatsCampaignBatchSize();

            if (!productStatsDone) {
                while (true) {
                    OzonProductStatsSyncResult productResult = campaignSyncService.syncProductStatsBatch(
                            cabinet,
                            clientId.trim(),
                            clientSecret.trim(),
                            dateFrom,
                            dateTo,
                            reportUuid,
                            batchStart
                    );
                    if (productResult.getStatus() == OzonProductStatsSyncResult.Status.PENDING) {
                        OzonCampaignStatsCabinetPayload pendingPayload = OzonCampaignStatsCabinetPayload.builder()
                                .dateFrom(dateFrom)
                                .dateTo(dateTo)
                                .productStatsReportUuid(productResult.getReportUuid())
                                .productStatsBatchStart(batchStart)
                                .productStatsDone(false)
                                .searchPhrasesReportUuid(payload != null ? payload.searchPhrasesReportUuid() : null)
                                .searchPhrasesBatchStart(payload != null ? payload.resolveSearchPhrasesBatchStart() : 0)
                                .build();
                        eventService.updateEventPayload(event.getId(), pendingPayload);
                        return OzonApiEventExecutionResult.deferredPoll(
                                "Ozon product-stats отчёт формируется (uuid=" + productResult.getReportUuid() + ")",
                                LocalDateTime.now().plusSeconds(20)
                        );
                    }
                    if (productResult.getStatus() == OzonProductStatsSyncResult.Status.COMPLETED) {
                        productRowsTotal += productResult.getRowsSaved();
                    }
                    if (productResult.getStatus() == OzonProductStatsSyncResult.Status.SKIPPED) {
                        log.warn("Ozon product stats batch skipped: cabinetId={}, batchStart={}, reason={}",
                                cabinet.getId(), batchStart, productResult.getMessage());
                    }

                    if (!campaignSyncService.hasMoreProductStatsBatches(cabinet.getId(), batchStart)) {
                        break;
                    }
                    batchStart += batchSize;
                    reportUuid = null;
                }
                productStatsDone = true;
            }

            int searchPhrasesRowsTotal = 0;
            int searchPhrasesBatchStart = payload != null ? payload.resolveSearchPhrasesBatchStart() : 0;
            String searchPhrasesReportUuid = payload != null ? payload.searchPhrasesReportUuid() : null;
            int searchPhrasesBatchSize = campaignSyncService.getSearchPhrasesCampaignBatchSize();

            while (true) {
                OzonProductStatsSyncResult phrasesResult = campaignSyncService.syncSearchPhrasesBatch(
                        cabinet,
                        clientId.trim(),
                        clientSecret.trim(),
                        dateFrom,
                        dateTo,
                        searchPhrasesReportUuid,
                        searchPhrasesBatchStart
                );
                if (phrasesResult.getStatus() == OzonProductStatsSyncResult.Status.PENDING) {
                    OzonCampaignStatsCabinetPayload pendingPayload = OzonCampaignStatsCabinetPayload.builder()
                            .dateFrom(dateFrom)
                            .dateTo(dateTo)
                            .productStatsDone(true)
                            .searchPhrasesReportUuid(phrasesResult.getReportUuid())
                            .searchPhrasesBatchStart(searchPhrasesBatchStart)
                            .build();
                    eventService.updateEventPayload(event.getId(), pendingPayload);
                    return OzonApiEventExecutionResult.deferredPoll(
                            "Ozon search-phrases отчёт формируется (uuid=" + phrasesResult.getReportUuid() + ")",
                            LocalDateTime.now().plusSeconds(20)
                    );
                }
                if (phrasesResult.getStatus() == OzonProductStatsSyncResult.Status.COMPLETED) {
                    searchPhrasesRowsTotal += phrasesResult.getRowsSaved();
                }
                if (phrasesResult.getStatus() == OzonProductStatsSyncResult.Status.SKIPPED) {
                    log.warn("Ozon search phrases batch skipped: cabinetId={}, batchStart={}, reason={}",
                            cabinet.getId(), searchPhrasesBatchStart, phrasesResult.getMessage());
                }

                if (!campaignSyncService.hasMoreSearchPhrasesBatches(cabinet.getId(), searchPhrasesBatchStart)) {
                    break;
                }
                searchPhrasesBatchStart += searchPhrasesBatchSize;
                searchPhrasesReportUuid = null;
            }

            eventService.markCampaignsSyncCompleted(cabinet.getId());
            log.info("Ozon campaign stats sync завершён для cabinetId={}, период={}..{}, daily={}, product={}, phrases={}",
                    cabinet.getId(), dateFrom, dateTo, statsRows, productRowsTotal, searchPhrasesRowsTotal);
            return OzonApiEventExecutionResult.completedSuccessfully();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                return OzonApiEventExecutionResult.deferredRetry(
                        "Rate limit Ozon Performance API",
                        LocalDateTime.now().plusSeconds(60)
                );
            }
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                return OzonApiEventExecutionResult.finalError(
                        "Ozon Performance API: невалидные credentials (HTTP " + e.getStatusCode().value() + ")");
            }
            return OzonApiEventExecutionResult.retryableError("Ozon Performance API: " + e.getStatusCode());
        } catch (Exception e) {
            return OzonApiEventExecutionResult.retryableError(e.getMessage());
        }
    }
}
