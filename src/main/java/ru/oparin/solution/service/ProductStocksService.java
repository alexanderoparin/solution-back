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

import java.util.List;
import java.util.Optional;

/**
 * Сервис для работы с остатками товаров на складах WB.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductStocksService {

    private final WbStocksApiClient stocksApiClient;
    private final ProductStockRepository stockRepository;
    private final ProductBarcodeRepository barcodeRepository;

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
                .filter(b -> chrtId.equals(b.getChrtId()))
                .map(ProductBarcode::getBarcode)
                .findFirst()
                .orElse(null);
    }

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

        // Формируем период (вчерашний день)
        java.time.LocalDate yesterday = java.time.LocalDate.now().minusDays(1);
        WbStocksSizesRequest.Period period = WbStocksSizesRequest.Period.builder()
                .start(yesterday.toString())
                .end(yesterday.toString())
                .build();

        // Формируем сортировку по остаткам на текущий день (по возрастанию)
        WbStocksSizesRequest.OrderBy orderBy = WbStocksSizesRequest.OrderBy.builder()
                .field("stockCount")
                .mode("asc")
                .build();

        // Формируем запрос с детализацией по складам
        WbStocksSizesRequest request = WbStocksSizesRequest.builder()
                .nmID(nmId)
                .currentPeriod(period)
                .stockType("wb") // Склады WB (не склады продавца)
                .orderBy(orderBy)
                .includeOffice(true) // Включаем детализацию по складам
                .build();

        // Получаем остатки с API
        WbStocksSizesResponse response = stocksApiClient.getWbStocksBySizes(apiKey, request);

        if (response == null || response.getData() == null 
                || response.getData().getSizes() == null 
                || response.getData().getSizes().isEmpty()) {
            log.warn("Не получено остатков по размерам на складах WB для артикула {}", nmId);
            return WbStocksSizesResponse.builder()
                    .data(WbStocksSizesResponse.Data.builder()
                            .sizes(List.of())
                            .build())
                    .build();
        }

        log.info("Получено {} размеров с остатками на складах WB для артикула {}", 
                response.getData().getSizes().size(), nmId);

        // Сохраняем остатки в БД (перезаписываем существующие записи)
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
        int savedCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;

        for (WbStocksSizesResponse.SizeItem sizeItem : sizeItems) {
            if (sizeItem == null || sizeItem.getName() == null) {
                skippedCount++;
                continue;
            }

            String sizeName = sizeItem.getName(); // Название размера (например, "M", "S")
            Long chrtID = sizeItem.getChrtID(); // ID характеристики размера

            // Если есть детализация по складам
            if (sizeItem.getOffices() != null && !sizeItem.getOffices().isEmpty()) {
                for (WbStocksSizesResponse.OfficeStock office : sizeItem.getOffices()) {
                    if (office == null || office.getOfficeID() == null || office.getMetrics() == null) {
                        continue;
                    }

                    Long stockCount = office.getMetrics().getStockCount();
                    if (stockCount == null) {
                        stockCount = 0L;
                    }

                    // Находим баркод по chrtID для этого размера
                    // Если баркодов несколько для одного chrtID, берем первый
                    String barcode = findBarcodeByChrtId(nmId, chrtID);
                    if (barcode == null || barcode.isEmpty()) {
                        log.warn("Не найден баркод для товара nmID {} и размера chrtID {}, пропускаем", 
                                nmId, chrtID);
                        skippedCount++;
                        continue;
                    }

                    // Проверяем, есть ли уже запись (без учета даты, так как перезаписываем каждый день)
                    Optional<ProductStock> existingStock = stockRepository.findByNmIdAndWarehouseIdAndBarcode(
                            nmId, office.getOfficeID(), barcode
                    );

                    try {
                        if (existingStock.isPresent()) {
                            // Обновляем существующую запись (включая обновление на 0, чтобы видеть историю)
                            ProductStock stock = existingStock.get();
                            stock.setAmount(stockCount.intValue());
                            stockRepository.save(stock);
                            updatedCount++;
                        } else {
                            // Создаем новую запись только если остатки не нулевые
                            if (stockCount > 0) {
                                ProductStock stock = new ProductStock();
                                stock.setNmId(nmId);
                                stock.setWarehouseId(office.getOfficeID());
                                stock.setBarcode(barcode);
                                stock.setAmount(stockCount.intValue());
                                stockRepository.save(stock);
                                savedCount++;
                            } else {
                                // Пропускаем создание новых записей с нулевыми остатками
                                skippedCount++;
                            }
                        }
                    } catch (Exception e) {
                        log.error("Ошибка при сохранении остатка для nmID {}, warehouseId {}, barcode {}: {}", 
                                nmId, office.getOfficeID(), barcode, e.getMessage(), e);
                        skippedCount++;
                        // Продолжаем обработку других записей
                    }
                }
            } else {
                // Если нет детализации по складам, пропускаем
                log.debug("Размер {} (chrtID: {}) для артикула {} не имеет детализации по складам, пропускаем", 
                        sizeName, chrtID, nmId);
                skippedCount++;
            }
        }

        log.info("Сохранено остатков по размерам на складах WB: создано {}, обновлено {}, пропущено {}", 
                savedCount, updatedCount, skippedCount);
    }
}

