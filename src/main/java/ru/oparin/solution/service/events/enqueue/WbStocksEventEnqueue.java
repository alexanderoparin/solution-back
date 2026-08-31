package ru.oparin.solution.service.events.enqueue;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.WbApiEventType;
import ru.oparin.solution.model.WbProductCard;
import ru.oparin.solution.service.WbProductCardService;
import ru.oparin.solution.service.events.WbApiEventExecutors;
import ru.oparin.solution.service.events.WbApiEventWriter;
import ru.oparin.solution.service.events.payload.WbMainStepPayload;
import ru.oparin.solution.service.events.payload.WbStocksByNmIdPayload;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Постановка событий складов и остатков WB (FBO/FBS).
 */
@Service
@RequiredArgsConstructor
public class WbStocksEventEnqueue {

    private static final int STOCKS_MAX_ATTEMPTS = 5;
    private static final int STOCKS_PRIORITY = 80;
    private static final int WAREHOUSES_MAX_ATTEMPTS = 5;
    private static final int WAREHOUSES_PRIORITY = 75;

    private final WbApiEventWriter writer;
    private final WbProductCardService productCardService;

    /**
     * Остатки FBO одного артикула.
     */
    @Transactional
    public void enqueueStocksByNmIdEvent(Long cabinetId, Long nmId, String triggerSource) {
        String dedupKey = "STOCKS_BY_NMID:" + cabinetId + ":" + nmId;
        WbStocksByNmIdPayload payload = WbStocksByNmIdPayload.builder().nmId(nmId).build();
        writer.insertIfAbsent(
                cabinetId,
                WbApiEventType.STOCKS_BY_NMID,
                WbApiEventExecutors.STOCKS,
                payload,
                dedupKey,
                STOCKS_MAX_ATTEMPTS,
                writer.resolveNmIdEventPriority(cabinetId, nmId, STOCKS_PRIORITY),
                triggerSource,
                null
        );
    }

    /**
     * Остатки всех артикулов кабинета + склады продавца FBS.
     */
    @Transactional
    public void enqueueAllStocksByNmIdForCabinet(Long cabinetId, String triggerSource) {
        List<Long> nmIds = productCardService.findByCabinetId(cabinetId).stream()
                .map(WbProductCard::getNmId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        for (Long nmId : nmIds) {
            enqueueStocksByNmIdEvent(cabinetId, nmId, triggerSource);
        }
        enqueueFbsWarehousesSyncCabinetEvent(cabinetId, triggerSource);
    }

    /**
     * Склады WB кабинета; всегда дополнительно ставит FBS warehouses.
     */
    @Transactional
    public void enqueueWarehousesSyncCabinetEvent(Long cabinetId, String triggerSource) {
        String dedupKey = "WAREHOUSES_SYNC_CABINET:" + cabinetId;
        LocalDate d = LocalDate.now();
        WbMainStepPayload payload = WbMainStepPayload.builder()
                .dateFrom(d)
                .dateTo(d)
                .includeStocks(false)
                .build();
        writer.insertIfAbsent(
                cabinetId,
                WbApiEventType.WAREHOUSES_SYNC_CABINET,
                WbApiEventExecutors.WAREHOUSES_SYNC,
                payload,
                dedupKey,
                WAREHOUSES_MAX_ATTEMPTS,
                WAREHOUSES_PRIORITY,
                triggerSource,
                null
        );
        enqueueFbsWarehousesSyncCabinetEvent(cabinetId, triggerSource);
    }

    /**
     * Склады продавца кабинета (Marketplace API). После успеха исполнитель ставит остатки FBS.
     */
    @Transactional
    public void enqueueFbsWarehousesSyncCabinetEvent(Long cabinetId, String triggerSource) {
        String dedupKey = "FBS_WAREHOUSES_SYNC_CABINET:" + cabinetId;
        LocalDate d = LocalDate.now();
        WbMainStepPayload payload = WbMainStepPayload.builder()
                .dateFrom(d)
                .dateTo(d)
                .includeStocks(false)
                .build();
        writer.insertIfAbsent(
                cabinetId,
                WbApiEventType.FBS_WAREHOUSES_SYNC_CABINET,
                WbApiEventExecutors.FBS_WAREHOUSES_SYNC,
                payload,
                dedupKey,
                WAREHOUSES_MAX_ATTEMPTS,
                WAREHOUSES_PRIORITY,
                triggerSource,
                null
        );
    }

    /**
     * Остатки FBS по всем складам продавца кабинета.
     */
    @Transactional
    public void enqueueFbsStocksCabinetEvent(Long cabinetId, String triggerSource) {
        String dedupKey = "FBS_STOCKS_CABINET:" + cabinetId;
        LocalDate d = LocalDate.now();
        WbMainStepPayload payload = WbMainStepPayload.builder()
                .dateFrom(d)
                .dateTo(d)
                .includeStocks(false)
                .build();
        writer.insertIfAbsent(
                cabinetId,
                WbApiEventType.FBS_STOCKS_CABINET,
                WbApiEventExecutors.FBS_STOCKS,
                payload,
                dedupKey,
                STOCKS_MAX_ATTEMPTS,
                STOCKS_PRIORITY,
                triggerSource,
                null
        );
    }
}
