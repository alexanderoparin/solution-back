package ru.oparin.solution.service.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.service.OzonProductStockService;
import ru.oparin.solution.service.ozon.OzonProductsApiClient;

/**
 * Синхронизация остатков Ozon из Seller API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OzonProductStocksSyncService {

    private static final int STOCKS_PAGE_LIMIT = 1000;

    private final OzonProductsApiClient productsApiClient;
    private final OzonProductStockService stockService;

    public void syncAllStocks(Cabinet cabinet, String clientId, String apiKey) {
        log.info("Ozon: загрузка остатков cabinetId={}", cabinet.getId());
        String cursor = "";
        int pages = 0;
        int totalItems = 0;
        do {
            var page = productsApiClient.listProductStocks(clientId, apiKey, cursor, STOCKS_PAGE_LIMIT);
            if (page.getItems() != null) {
                totalItems += page.getItems().size();
                stockService.saveStocks(cabinet, page);
            }
            pages++;
            cursor = page.getCursor();
            if (cursor == null) {
                cursor = "";
            }
        } while (!cursor.isBlank());
        log.info("Ozon: остатки загружены cabinetId={}, страниц={}, позиций={}", cabinet.getId(), pages, totalItems);
    }
}
