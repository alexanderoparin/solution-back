package ru.oparin.solution.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.wb.WbStocksSizesRequest;
import ru.oparin.solution.dto.wb.WbStocksSizesResponse;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.ProductBarcode;
import ru.oparin.solution.model.ProductStock;
import ru.oparin.solution.repository.ProductBarcodeRepository;
import ru.oparin.solution.repository.ProductStockRepository;
import ru.oparin.solution.service.wb.WbStocksApiClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    /** Задержка между запросами остатков (лимит WB: 3 запроса в минуту). */
    private static final long STOCKS_REQUEST_DELAY_MS = 20000;
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
        return getWbStocksBySizes(apiKey, nmId, null);
    }

    @Transactional
    public WbStocksSizesResponse getWbStocksBySizes(String apiKey, Long nmId, Cabinet cabinet) {
        WbStocksSizesRequest request = buildStocksRequest(nmId);
        WbStocksSizesResponse response = stocksApiClient.getWbStocksBySizes(apiKey, request);

        if (isEmptyResponse(response)) {
            log.warn("Не получено остатков по размерам на складах WB для артикула {}", nmId);
            return createEmptyResponse();
        }

        log.info("Получено {} размеров с остатками на складах WB для артикула {}", response.getData().getSizes().size(), nmId);

        saveWbStocksBySizes(nmId, response.getData().getSizes(), cabinet);
        return response;
    }

    /**
     * Обновляет остатки по размерам на складах WB для всех указанных артикулов кабинета.
     * Между запросами пауза 20 с (лимит API).
     */
    public void updateStocksForCabinet(Cabinet cabinet, String apiKey, java.util.List<Long> nmIds) {
        if (nmIds == null || nmIds.isEmpty()) {
            return;
        }
        log.info("Начало обновления остатков товаров на складах WB для кабинета (ID: {}), товаров: {}", cabinet.getId(), nmIds.size());
        int count = 0;
        for (Long nmId : nmIds) {
            try {
                getWbStocksBySizes(apiKey, nmId, cabinet);
                count++;
                if (count < nmIds.size()) {
                    Thread.sleep(STOCKS_REQUEST_DELAY_MS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Прервано ожидание перед запросом остатков для артикула {}", nmId);
                break;
            } catch (Exception e) {
                log.error("Ошибка при обновлении остатков для артикула {}: {}", nmId, e.getMessage(), e);
            }
        }
        log.info("Завершено обновление остатков товаров на складах WB для кабинета (ID: {})", cabinet.getId());
    }

    /**
     * Сохраняет остатки товаров по размерам на складах WB в БД.
     * Данные перезаписываются, время записи фиксируется в created_at и updated_at.
     *
     * @param nmId артикул товара
     * @param sizeItems список размеров с остатками
     */
    private void saveWbStocksBySizes(Long nmId, List<WbStocksSizesResponse.SizeItem> sizeItems, Cabinet cabinet) {
        if (cabinet == null) {
            log.warn("Кабинет не указан, остатки не сохранены");
            return;
        }
        SaveStatistics statistics = new SaveStatistics();

        for (WbStocksSizesResponse.SizeItem sizeItem : sizeItems) {
            if (!isValidSizeItem(sizeItem)) {
                statistics.incrementSkipped();
                continue;
            }

            processSizeItem(nmId, sizeItem, statistics, cabinet);
        }

        logSaveStatistics(statistics);
    }

    /**
     * Обрабатывает один размер товара.
     * Учитывает случаи:
     * - Товар с размерами (chrtID != null) - ищем баркод по chrtID
     * - Товар без размеров (chrtID == null, techSize="0") - берем первый баркод товара
     */
    private void processSizeItem(Long nmId, WbStocksSizesResponse.SizeItem sizeItem, SaveStatistics statistics, Cabinet cabinet) {
        if (!hasOfficeDetails(sizeItem)) {
            statistics.incrementSkipped();
            return;
        }

        String barcode = findBarcodeForSizeItem(nmId, sizeItem, cabinet.getId());
        if (barcode == null || barcode.isEmpty()) {
            log.warn("Не найден баркод для товара nmID {} и размера chrtID {} в кабинете {}, пропускаем", nmId, sizeItem.getChrtID(), cabinet.getId());
            statistics.incrementSkipped();
            return;
        }

        for (WbStocksSizesResponse.OfficeStock office : sizeItem.getOffices()) {
            if (isValidOffice(office)) {
                processOfficeStock(nmId, office, barcode, statistics, cabinet);
            }
        }
    }

    /**
     * Обрабатывает остатки на одном складе.
     */
    private void processOfficeStock(Long nmId, WbStocksSizesResponse.OfficeStock office,
                                    String barcode, SaveStatistics statistics, Cabinet cabinet) {
        Long stockCount = getStockCount(office);
        Optional<ProductStock> existingStock = findExistingStock(nmId, office.getOfficeID(), barcode, cabinet.getId());

        try {
            if (existingStock.isPresent()) {
                updateExistingStock(existingStock.get(), stockCount);
                statistics.incrementUpdated();
            } else {
                if (shouldCreateNewStock(stockCount)) {
                    createNewStock(nmId, office.getOfficeID(), barcode, stockCount, cabinet);
                    statistics.incrementSaved();
                } else {
                    statistics.incrementSkipped();
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при сохранении остатка для nmID {}, warehouseId {}, barcode {}, amount {}: {}",
                    nmId, office.getOfficeID(), barcode, stockCount, e.getMessage());
            statistics.incrementSkipped();
        }
    }

    /**
     * Обновляет существующую запись об остатках.
     */
    private void updateExistingStock(ProductStock stock, Long stockCount) {
        stock.setAmount(stockCount.intValue());
        stock.setUpdatedAt(LocalDateTime.now());
        stockRepository.save(stock);
    }

    private void createNewStock(Long nmId, Long warehouseId, String barcode, Long stockCount, Cabinet cabinet) {
        ProductStock stock = ProductStock.builder()
                .nmId(nmId)
                .cabinet(cabinet)
                .warehouseId(warehouseId)
                .barcode(barcode)
                .amount(stockCount.intValue())
                .build();
        stockRepository.save(stock);
    }

    /**
     * Находит баркод для размера товара.
     * Для товаров с размерами (chrtID != null) ищет по chrtID.
     * Для товаров без размеров (chrtID == null) берет первый баркод товара.
     *
     * @param nmId артикул товара
     * @param sizeItem элемент размера из ответа API
     * @return баркод или null, если не найден
     */
    private String findBarcodeForSizeItem(Long nmId, WbStocksSizesResponse.SizeItem sizeItem, Long cabinetId) {
        Long chrtId = sizeItem.getChrtID();
        List<ProductBarcode> barcodes = cabinetId != null
                ? barcodeRepository.findByNmIdAndCabinet_Id(nmId, cabinetId)
                : barcodeRepository.findByNmId(nmId);

        if (chrtId != null) {
            return barcodes.stream()
                    .filter(b -> chrtId.equals(b.getChrtId()))
                    .map(ProductBarcode::getBarcode)
                    .findFirst()
                    .orElse(null);
        }
        return barcodes.stream()
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
     * Для товаров без размеров name может быть null или пустым,
     * но сам sizeItem должен существовать.
     */
    private boolean isValidSizeItem(WbStocksSizesResponse.SizeItem sizeItem) {
        return sizeItem != null;
    }

    /**
     * Проверяет наличие детализации по складам.
     */
    private boolean hasOfficeDetails(WbStocksSizesResponse.SizeItem sizeItem) {
        return sizeItem.getOffices() != null && !sizeItem.getOffices().isEmpty();
    }

    /**
     * Проверяет валидность склада.
     * Учитывает, что склады продавца могут иметь regionName="Маркетплейс" и officeName="",
     * но officeID должен быть заполнен.
     */
    private boolean isValidOffice(WbStocksSizesResponse.OfficeStock office) {
        if (office == null || office.getOfficeID() == null || office.getMetrics() == null) {
            return false;
        }
        
        // Склады продавца приходят с regionName="Маркетплейс" и officeName=""
        // Это валидные данные, их тоже нужно обрабатывать
        return true;
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

    private Optional<ProductStock> findExistingStock(Long nmId, Long warehouseId, String barcode, Long cabinetId) {
        return stockRepository.findByNmIdAndWarehouseIdAndBarcodeAndCabinetId(nmId, warehouseId, barcode, cabinetId);
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
     * Класс для подсчёта статистики сохранения.
     */
    @Getter
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
    }
}

