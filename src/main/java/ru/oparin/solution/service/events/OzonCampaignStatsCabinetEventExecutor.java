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
            if (payload == null || payload.productStatsReportUuid() == null || payload.productStatsReportUuid().isBlank()) {
                statsRows = campaignSyncService.syncCampaignsAndDailyStats(
                        cabinet, clientId.trim(), clientSecret.trim(), dateFrom, dateTo);
            }

            int batchStart = payload != null ? payload.resolveProductStatsBatchStart() : 0;
            String reportUuid = payload != null ? payload.productStatsReportUuid() : null;
            int productRowsTotal = 0;
            int batchSize = campaignSyncService.getProductStatsCampaignBatchSize();

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

                if (!campaignSyncService.hasMoreProductStatsBatches(cabinet.getId(), batchStart)) {
                    break;
                }
                batchStart += batchSize;
                reportUuid = null;
            }

            eventService.markCampaignsSyncCompleted(cabinet.getId());
            log.info("Ozon campaign stats sync завершён для cabinetId={}, период={}..{}, daily={}, product={}",
                    cabinet.getId(), dateFrom, dateTo, statsRows, productRowsTotal);
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
