package ru.oparin.solution.service.events.enqueue;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.WbApiEventType;
import ru.oparin.solution.service.events.WbApiEventExecutors;
import ru.oparin.solution.service.events.WbApiEventWriter;
import ru.oparin.solution.service.events.payload.WbMainStepPayload;

/**
 * Постановка события цен кабинета WB (+ СПП).
 */
@Service
@RequiredArgsConstructor
public class WbPricesEventEnqueue {

    private static final int MAX_ATTEMPTS = 5;
    private static final int PRIORITY = 85;

    private final WbApiEventWriter writer;

    /**
     * Одно событие: цены всеми батчами внутри исполнителя, затем СПП.
     */
    @Transactional
    public void enqueuePricesRequestLevelEvents(Long cabinetId, WbMainStepPayload payload, String triggerSource) {
        String dedupKey = "PRICES_CABINET_WITH_SPP:" + cabinetId + ":" + payload.dateFrom() + ":" + payload.dateTo();
        writer.insertIfAbsent(
                cabinetId,
                WbApiEventType.PRICES_CABINET_WITH_SPP,
                WbApiEventExecutors.PRICES_CABINET_WITH_SPP,
                payload,
                dedupKey,
                MAX_ATTEMPTS,
                PRIORITY,
                triggerSource,
                null
        );
    }
}
