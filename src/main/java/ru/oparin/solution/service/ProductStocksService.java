package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.wb.ProductStocksRequest;
import ru.oparin.solution.dto.wb.ProductStocksResponse;
import ru.oparin.solution.model.ProductBarcode;
import ru.oparin.solution.model.ProductStock;
import ru.oparin.solution.repository.ProductBarcodeRepository;
import ru.oparin.solution.repository.ProductStockRepository;
import ru.oparin.solution.service.wb.WbStocksApiClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Сервис для работы с остатками товаров.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductStocksService {

    private final WbStocksApiClient stocksApiClient;
    private final ProductBarcodeRepository barcodeRepository;
    private final ProductStockRepository stockRepository;

    /**
     * Получает и сохраняет остатки товаров на складе продавца.
     *
     * @param apiKey API ключ продавца
     * @param warehouseId ID склада продавца
     * @param nmIds список артикулов товаров (nmID)
     * @return ответ с остатками товаров
     */
    @Transactional
    public ProductStocksResponse getStocks(String apiKey, Long warehouseId, List<Long> nmIds) {
        if (nmIds == null || nmIds.isEmpty()) {
            log.warn("Список nmID пуст, возвращаем пустой ответ");
            return ProductStocksResponse.builder()
                    .stocks(new ArrayList<>())
                    .build();
        }

        // Получаем все баркоды для указанных товаров
        List<ProductBarcode> barcodes = barcodeRepository.findByNmIdIn(nmIds);
        
        if (barcodes.isEmpty()) {
            log.warn("Не найдено баркодов для товаров: {}", nmIds);
            return ProductStocksResponse.builder()
                    .stocks(new ArrayList<>())
                    .build();
        }

        List<String> skus = barcodes.stream()
                .map(ProductBarcode::getSku)
                .distinct()
                .collect(Collectors.toList());

        log.info("Найдено {} уникальных баркодов для {} товаров", skus.size(), nmIds.size());

        // Разбиваем на батчи по 1000 баркодов (лимит API)
        List<ProductStocksResponse> responses = new ArrayList<>();
        int batchSize = 1000;

        for (int i = 0; i < skus.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, skus.size());
            List<String> batchSkus = skus.subList(i, endIndex);

            ProductStocksRequest request = ProductStocksRequest.builder()
                    .skus(batchSkus)
                    .build();

            log.info("Запрос остатков для батча {}/{} ({} баркодов)", 
                    (i / batchSize) + 1, 
                    (skus.size() + batchSize - 1) / batchSize,
                    batchSkus.size());

            ProductStocksResponse response = stocksApiClient.getStocks(apiKey, warehouseId, request);
            if (response != null && response.getStocks() != null) {
                responses.add(response);
            }
        }

        // Объединяем результаты всех батчей
        List<ProductStocksResponse.Stock> allStocks = responses.stream()
                .flatMap(r -> r.getStocks() != null ? r.getStocks().stream() : java.util.stream.Stream.empty())
                .collect(Collectors.toList());

        log.info("Получено остатков товаров: {}", allStocks.size());

        // Сохраняем остатки в БД
        if (!allStocks.isEmpty()) {
            saveStocks(allStocks, warehouseId, barcodes);
        }

        return ProductStocksResponse.builder()
                .stocks(allStocks)
                .build();
    }

    /**
     * Сохраняет остатки товаров в БД.
     *
     * @param stocks список остатков из API
     * @param warehouseId ID склада продавца
     * @param barcodes список баркодов для маппинга sku -> nmId
     */
    private void saveStocks(
            List<ProductStocksResponse.Stock> stocks,
            Long warehouseId,
            List<ProductBarcode> barcodes
    ) {
        LocalDate yesterdayDate = LocalDate.now().minusDays(1);
        
        // Создаем мапу sku -> nmId для быстрого поиска
        Map<String, Long> skuToNmIdMap = barcodes.stream()
                .collect(Collectors.toMap(
                        ProductBarcode::getSku,
                        ProductBarcode::getNmId,
                        (existing, replacement) -> existing // Если есть дубликаты, берем первый
                ));

        int savedCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;

        for (ProductStocksResponse.Stock stockDto : stocks) {
            if (stockDto == null || stockDto.getSku() == null || stockDto.getAmount() == null) {
                skippedCount++;
                continue;
            }

            // Находим nmId по sku
            Long nmId = skuToNmIdMap.get(stockDto.getSku());
            if (nmId == null) {
                log.warn("Не найден nmId для баркода: {}", stockDto.getSku());
                skippedCount++;
                continue;
            }

            // Проверяем, есть ли уже запись
            Optional<ProductStock> existingStock = stockRepository.findByNmIdAndWarehouseIdAndSkuAndDate(
                    nmId, warehouseId, stockDto.getSku(), yesterdayDate
            );

            if (existingStock.isPresent()) {
                // Обновляем существующую запись
                ProductStock stock = existingStock.get();
                stock.setAmount(stockDto.getAmount());
                stockRepository.save(stock);
                updatedCount++;
            } else {
                // Создаем новую запись
                ProductStock stock = ProductStock.builder()
                        .nmId(nmId)
                        .warehouseId(warehouseId)
                        .sku(stockDto.getSku())
                        .amount(stockDto.getAmount())
                        .date(yesterdayDate)
                        .build();
                stockRepository.save(stock);
                savedCount++;
            }
        }

        log.info("Сохранено остатков: создано {}, обновлено {}, пропущено {}", 
                savedCount, updatedCount, skippedCount);
    }
}

