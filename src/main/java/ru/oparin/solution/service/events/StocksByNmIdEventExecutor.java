package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.model.WbApiEventStatus;
import ru.oparin.solution.model.WbApiEventType;
import ru.oparin.solution.repository.WbApiEventRepository;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.ProductStocksService;
import ru.oparin.solution.service.events.payload.StocksByNmIdPayload;

import java.time.LocalDateTime;
import java.util.Set;

@Component("stocksByNmIdEventExecutor")
@RequiredArgsConstructor
@Slf4j
public class StocksByNmIdEventExecutor implements WbApiEventExecutor {

    /**
     * Статусы «ещё в очереди / ждут повтора». RUNNING намеренно не включаем: эта проверка вызывается
     * из execute() до markSuccess, иначе текущее событие всегда считалось бы «висящим» и
     * lastStocksUpdateAt никогда не обновлялся бы.
     */
    private static final Set<WbApiEventStatus> PENDING_STOCKS_STATUSES = Set.of(
            WbApiEventStatus.CREATED,
            WbApiEventStatus.FAILED_RETRYABLE,
            WbApiEventStatus.DEFERRED_RATE_LIMIT
    );

    private final WbApiEventService eventService;
    private final ProductStocksService productStocksService;
    private final CabinetService cabinetService;
    private final WbApiEventRepository eventRepository;

    @Override
    public WbApiEventExecutionResult execute(WbApiEvent event) {
        StocksByNmIdPayload payload = eventService.readPayload(event, StocksByNmIdPayload.class);
        Cabinet cabinet = cabinetService.findByIdWithUserOrThrow(event.getCabinet().getId());
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            return WbApiEventExecutionResult.finalError("У кабинета отсутствует API ключ");
        }
        try {
            productStocksService.getWbStocksBySizes(cabinet.getApiKey(), payload.nmId(), cabinet);
            markStocksCompletedIfLastEvent(cabinet.getId());
            return WbApiEventExecutionResult.completedSuccessfully();
        } catch (Exception e) {
            return WbEventExecutionErrors.wrapDeferOrRetryable(e);
        }
    }

    private void markStocksCompletedIfLastEvent(Long cabinetId) {
        boolean hasPendingStocks = eventRepository.existsByCabinet_IdAndEventTypeAndStatusIn(
                cabinetId,
                WbApiEventType.STOCKS_BY_NMID,
                PENDING_STOCKS_STATUSES
        );
        if (!hasPendingStocks) {
            Cabinet cabinet = cabinetService.findByIdWithUserOrThrow(cabinetId);
            cabinet.setLastStocksUpdateAt(LocalDateTime.now());
            cabinetService.save(cabinet);
        }
    }
}
