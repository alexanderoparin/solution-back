package ru.oparin.solution.service.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.oparin.solution.dto.ozon.OzonProductInfoPricesResponse;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.service.OzonProductPriceService;
import ru.oparin.solution.service.ozon.OzonProductsApiClient;

import java.time.LocalDate;

/**
 * Синхронизация цен Ozon (без SPP) из Seller API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OzonProductPricesSyncService {

    private static final int PRICES_PAGE_LIMIT = 1000;

    private final OzonProductsApiClient productsApiClient;
    private final OzonProductPriceService productPriceService;

    /**
     * Загружает все страницы цен и сохраняет снимок на вчерашнюю дату.
     */
    public void syncAllPrices(Cabinet cabinet, String clientId, String apiKey) {
        LocalDate snapshotDate = LocalDate.now().minusDays(1);
        log.info("Ozon: загрузка цен cabinetId={}, date={}", cabinet.getId(), snapshotDate);
        String cursor = "";
        int pages = 0;
        int totalItems = 0;
        do {
            OzonProductInfoPricesResponse page = productsApiClient.listProductPrices(
                    clientId, apiKey, cursor, PRICES_PAGE_LIMIT);
            if (page.getItems() != null) {
                totalItems += page.getItems().size();
                productPriceService.savePrices(cabinet, page, snapshotDate);
            }
            pages++;
            cursor = page.getCursor();
            if (cursor == null) {
                cursor = "";
            }
        } while (!cursor.isBlank());
        log.info("Ozon: цены загружены cabinetId={}, страниц={}, позиций={}", cabinet.getId(), pages, totalItems);
    }
}
