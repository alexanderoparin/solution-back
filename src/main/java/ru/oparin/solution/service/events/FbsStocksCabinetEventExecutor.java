package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.oparin.solution.exception.WbApiUnauthorizedScopeException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.service.CabinetScopeStatusService;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.ProductFbsStocksService;
import ru.oparin.solution.service.wb.WbApiCategory;

/**
 * Синхронизация остатков FBS по всем складам продавца кабинета.
 */
@Component("fbsStocksCabinetEventExecutor")
@RequiredArgsConstructor
@Slf4j
public class FbsStocksCabinetEventExecutor implements WbApiEventExecutor {

    private final CabinetService cabinetService;
    private final ProductFbsStocksService productFbsStocksService;
    private final CabinetScopeStatusService cabinetScopeStatusService;

    @Override
    public WbApiEventExecutionResult execute(WbApiEvent event) {
        Cabinet cabinet = cabinetService.findByIdWithUserOrThrow(event.getCabinet().getId());
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            return WbApiEventExecutionResult.finalError("У кабинета отсутствует API ключ");
        }
        try {
            productFbsStocksService.syncCabinet(cabinet.getApiKey(), cabinet);
            cabinetScopeStatusService.recordSuccess(cabinet.getId(), WbApiCategory.MARKETPLACE);
            return WbApiEventExecutionResult.completedSuccessfully();
        } catch (WbApiUnauthorizedScopeException e) {
            cabinetScopeStatusService.recordFailure(cabinet.getId(), e.getCategory(), e.getMessage());
            log.warn("Не удалось обновить остатки FBS кабинета {}, нет доступа к категории WB API: {}",
                    cabinet.getId(), e.getCategory().getDisplayName());
            return WbApiEventExecutionResult.completedSuccessfully();
        } catch (Exception e) {
            return WbEventExecutionErrors.wrapDeferOrRetryable(e);
        }
    }
}
