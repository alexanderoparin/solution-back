package ru.oparin.solution.service.events.enqueue;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.WbApiEventType;
import ru.oparin.solution.service.events.WbApiEventExecutors;
import ru.oparin.solution.service.events.WbApiEventWriter;
import ru.oparin.solution.service.events.payload.WbAnalyticsSalesFunnelPayload;

import java.time.LocalDate;

/**
 * Постановка событий воронки продаж WB по nmId.
 */
@Service
@RequiredArgsConstructor
public class WbAnalyticsFunnelEventEnqueue {

    private static final int MAX_ATTEMPTS = 5;
    private static final int PRIORITY = 90;

    private final WbApiEventWriter writer;

    /**
     * Воронка продаж одного артикула за период.
     */
    @Transactional
    public void enqueueAnalyticsSalesFunnelEvent(
            Long cabinetId,
            Long nmId,
            LocalDate dateFrom,
            LocalDate dateTo,
            boolean includeStocks,
            String triggerSource
    ) {
        String dedupKey = "ANALYTICS_SALES_FUNNEL_NMID:" + cabinetId + ":" + nmId + ":" + dateFrom + ":" + dateTo;
        WbAnalyticsSalesFunnelPayload payload = WbAnalyticsSalesFunnelPayload.builder()
                .nmId(nmId)
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .includeStocks(includeStocks)
                .build();
        writer.insertIfAbsent(
                cabinetId,
                WbApiEventType.ANALYTICS_SALES_FUNNEL_NMID,
                WbApiEventExecutors.ANALYTICS,
                payload,
                dedupKey,
                MAX_ATTEMPTS,
                writer.resolveNmIdEventPriority(cabinetId, nmId, PRIORITY),
                triggerSource,
                null
        );
    }
}
