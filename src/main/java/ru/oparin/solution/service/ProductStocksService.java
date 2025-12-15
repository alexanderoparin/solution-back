package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.wb.WbStocksSizesRequest;
import ru.oparin.solution.dto.wb.WbStocksSizesResponse;
import ru.oparin.solution.model.ProductBarcode;
import ru.oparin.solution.model.ProductStock;
import ru.oparin.solution.repository.ProductBarcodeRepository;
import ru.oparin.solution.repository.ProductStockRepository;
import ru.oparin.solution.service.wb.WbStocksApiClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Сервис для работы с остатками товаров на складах WB.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductStocksService {

    private static final String STOCK_TYPE_WB = "wb";
    private static final String ORDER_FIELD_STOCK_COUNT = "stockCount";
    private static final String ORDER_MODE_ASC = "asc";
    private static final long DEFAULT_STOCK_COUNT = 0L;

    private final WbStocksApiClient stocksApiClient;
    private final ProductStockRepository stockRepository;
    private final ProductBarcodeRepository barcodeRepository;

    /**
     * Получает и сохраняет остатки товаров по размерам на складах WB.
     * Данные перезаписываются каждый день, время записи фиксируется в created_at и updated_at.
     *
     * @param apiKey API ключ продавца (токен для категории "Аналитика")
     * @param nmId артикул товара (nmID)
     * @return ответ с остатками товаров по размерам
     */
    @Transactional
    public WbStocksSizesResponse getWbStocksBySizes(String apiKey, Long nmId) {
        log.info("Начало загрузки остатков по размерам на складах WB для артикула {}", nmId);

        WbStocksSizesRequest request = buildStocksRequest(nmId);
        WbStocksSizesResponse response = stocksApiClient.getWbStocksBySizes(apiKey, request);

        if (isEmptyResponse(response)) {
            log.warn("Не получено остатков по размерам на складах WB для артикула {}", nmId);
            return createEmptyResponse();
        }

        log.info("Получено {} размеров с остатками на складах WB для артикула {}", 
                response.getData().getSizes().size(), nmId);

        saveWbStocksBySizes(nmId, response.getData().getSizes());
        return response;
    }

    /**
     * Сохраняет остатки товаров по размерам на складах WB в БД.
     * Данные перезаписываются, время записи фиксируется в created_at и updated_at.
     *
     * @param nmId артикул товара
     * @param sizeItems список размеров с остатками
     */
    private void saveWbStocksBySizes(Long nmId, List<WbStocksSizesResponse.SizeItem> sizeItems) {
        SaveStatistics statistics = new SaveStatistics();

        for (WbStocksSizesResponse.SizeItem sizeItem : sizeItems) {
            if (!isValidSizeItem(sizeItem)) {
                statistics.incrementSkipped();
                continue;
            }

            processSizeItem(nmId, sizeItem, statistics);
        }

        logSaveStatistics(statistics);
    }

    /**
     * Обрабатывает один размер товара.
     */
    private void processSizeItem(Long nmId, WbStocksSizesResponse.SizeItem sizeItem, SaveStatistics statistics) {
        if (!hasOfficeDetails(sizeItem)) {
            log.debug("Размер {} (chrtID: {}) для артикула {} не имеет детализации по складам, пропускаем",
                    sizeItem.getName(), sizeItem.getChrtID(), nmId);
            statistics.incrementSkipped();
            return;
        }

        String barcode = findBarcodeByChrtId(nmId, sizeItem.getChrtID());
        if (barcode == null || barcode.isEmpty()) {
            log.warn("Не найден баркод для товара nmID {} и размера chrtID {}, пропускаем", nmId, sizeItem.getChrtID());
            statistics.incrementSkipped();
            return;
        }

        for (WbStocksSizesResponse.OfficeStock office : sizeItem.getOffices()) {
            if (isValidOffice(office)) {
                processOfficeStock(nmId, office, barcode, statistics);
            }
        }
    }

    /**
     * Обрабатывает остатки на одном складе.
     */
    private void processOfficeStock(Long nmId, WbStocksSizesResponse.OfficeStock office, 
                                    String barcode, SaveStatistics statistics) {
        Long stockCount = getStockCount(office);
        Optional<ProductStock> existingStock = findExistingStock(nmId, office.getOfficeID(), barcode);

        try {
            if (existingStock.isPresent()) {
                updateExistingStock(existingStock.get(), stockCount);
                statistics.incrementUpdated();
            } else {
                if (shouldCreateNewStock(stockCount)) {
                    createNewStock(nmId, office.getOfficeID(), barcode, stockCount);
                    statistics.incrementSaved();
                } else {
                    statistics.incrementSkipped();
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при сохранении остатка для nmID {}, warehouseId {}, barcode {}: {}",
                    nmId, office.getOfficeID(), barcode, e.getMessage(), e);
            statistics.incrementSkipped();
        }
    }

    /**
     * Обновляет существующую запись об остатках.
     */
    private void updateExistingStock(ProductStock stock, Long stockCount) {
        stock.setAmount(stockCount.intValue());
        stockRepository.save(stock);
    }

    /**
     * Создает новую запись об остатках.
     */
    private void createNewStock(Long nmId, Long warehouseId, String barcode, Long stockCount) {
        ProductStock stock = ProductStock.builder()
                .nmId(nmId)
                .warehouseId(warehouseId)
                .barcode(barcode)
                .amount(stockCount.intValue())
                .build();
        stockRepository.save(stock);
    }

    /**
     * Находит баркод по nmID и chrtID.
     * Если баркодов несколько для одного chrtID, возвращает первый.
     *
     * @param nmId артикул товара
     * @param chrtId ID характеристики размера
     * @return баркод или null, если не найден
     */
    private String findBarcodeByChrtId(Long nmId, Long chrtId) {
        if (chrtId == null) {
            return null;
        }

        List<ProductBarcode> barcodes = barcodeRepository.findByNmId(nmId);
        return barcodes.stream()
                .filter(barcode -> chrtId.equals(barcode.getChrtId()))
                .map(ProductBarcode::getBarcode)
                .findFirst()
                .orElse(null);
    }

    /**
     * Создает запрос для получения остатков.
     */
    private WbStocksSizesRequest buildStocksRequest(Long nmId) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        WbStocksSizesRequest.Period period = WbStocksSizesRequest.Period.builder()
                .start(yesterday.toString())
                .end(yesterday.toString())
                .build();

        WbStocksSizesRequest.OrderBy orderBy = WbStocksSizesRequest.OrderBy.builder()
                .field(ORDER_FIELD_STOCK_COUNT)
                .mode(ORDER_MODE_ASC)
                .build();

        return WbStocksSizesRequest.builder()
                .nmID(nmId)
                .currentPeriod(period)
                .stockType(STOCK_TYPE_WB)
                .orderBy(orderBy)
                .includeOffice(true)
                .build();
    }

    /**
     * Проверяет, является ли ответ пустым.
     */
    private boolean isEmptyResponse(WbStocksSizesResponse response) {
        return response == null
                || response.getData() == null
                || response.getData().getSizes() == null
                || response.getData().getSizes().isEmpty();
    }

    /**
     * Создает пустой ответ.
     */
    private WbStocksSizesResponse createEmptyResponse() {
        return WbStocksSizesResponse.builder()
                .data(WbStocksSizesResponse.Data.builder()
                        .sizes(List.of())
                        .build())
                .build();
    }

    /**
     * Проверяет валидность размера.
     */
    private boolean isValidSizeItem(WbStocksSizesResponse.SizeItem sizeItem) {
        return sizeItem != null && sizeItem.getName() != null;
    }

    /**
     * Проверяет наличие детализации по складам.
     */
    private boolean hasOfficeDetails(WbStocksSizesResponse.SizeItem sizeItem) {
        return sizeItem.getOffices() != null && !sizeItem.getOffices().isEmpty();
    }

    /**
     * Проверяет валидность склада.
     */
    private boolean isValidOffice(WbStocksSizesResponse.OfficeStock office) {
        return office != null
                && office.getOfficeID() != null
                && office.getMetrics() != null;
    }

    /**
     * Получает количество остатков на складе.
     */
    private Long getStockCount(WbStocksSizesResponse.OfficeStock office) {
        Long stockCount = office.getMetrics().getStockCount();
        return stockCount != null ? stockCount : DEFAULT_STOCK_COUNT;
    }

    /**
     * Находит существующую запись об остатках.
     */
    private Optional<ProductStock> findExistingStock(Long nmId, Long warehouseId, String barcode) {
        return stockRepository.findByNmIdAndWarehouseIdAndBarcode(nmId, warehouseId, barcode);
    }

    /**
     * Определяет, нужно ли создавать новую запись.
     */
    private boolean shouldCreateNewStock(Long stockCount) {
        return stockCount > 0;
    }

    /**
     * Логирует статистику сохранения.
     */
    private void logSaveStatistics(SaveStatistics statistics) {
        log.info("Сохранено остатков по размерам на складах WB: создано {}, обновлено {}, пропущено {}",
                statistics.getSaved(), statistics.getUpdated(), statistics.getSkipped());
    }

    /**
     * Класс для подсчета статистики сохранения.
     */
    private static class SaveStatistics {
        private int saved = 0;
        private int updated = 0;
        private int skipped = 0;

        void incrementSaved() {
            saved++;
        }

        void incrementUpdated() {
            updated++;
        }

        void incrementSkipped() {
            skipped++;
        }

        int getSaved() {
            return saved;
        }

        int getUpdated() {
            return updated;
        }

        int getSkipped() {
            return skipped;
        }
    }
}

