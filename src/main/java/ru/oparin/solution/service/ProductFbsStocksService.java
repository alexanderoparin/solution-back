package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import ru.oparin.solution.dto.wb.WbFbsStocksResponse;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.ProductBarcode;
import ru.oparin.solution.model.ProductFbsStock;
import ru.oparin.solution.model.SellerWarehouse;
import ru.oparin.solution.repository.ProductBarcodeRepository;
import ru.oparin.solution.repository.ProductFbsStockRepository;
import ru.oparin.solution.service.wb.WbFbsApiClient;

import java.util.*;

/**
 * Синхронизация остатков FBS: POST /api/v3/stocks/{warehouseId} пачками по 1000 chrtId.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductFbsStocksService {

    /**
     * Лимит WB: не более 1000 chrtId в одном запросе остатков.
     */
    private static final int CHRT_BATCH_SIZE = 1000;

    private final WbFbsApiClient fbsApiClient;
    private final SellerWarehouseService sellerWarehouseService;
    private final ProductBarcodeRepository barcodeRepository;
    private final ProductFbsStockRepository fbsStockRepository;

    /**
     * self-proxy, чтобы {@code @Transactional} на сохранении снимка не обходился самовызовом.
     */
    @Lazy
    @Autowired
    private ProductFbsStocksService self;

    /**
     * Запрашивает остатки FBS по всем складам продавца кабинета и сохраняет снимок.
     *
     * @param apiKey  токен с категорией «Маркетплейс»
     * @param cabinet кабинет
     */
    public void syncCabinet(String apiKey, Cabinet cabinet) {
        List<SellerWarehouse> warehouses = sellerWarehouseService.findByCabinetId(cabinet.getId());
        if (warehouses.isEmpty()) {
            log.info("Нет складов продавца для синхронизации остатков FBS, cabinetId={}", cabinet.getId());
            return;
        }

        List<ProductBarcode> barcodes = barcodeRepository.findByCabinet_Id(cabinet.getId());
        List<Long> chrtIds = distinctChrtIds(barcodes);
        if (chrtIds.isEmpty()) {
            log.info("Нет chrtId в product_barcodes для остатков FBS, cabinetId={}", cabinet.getId());
            return;
        }

        Map<Long, ProductBarcode> barcodeByChrtId = firstBarcodeByChrtId(barcodes);
        log.info("Синхронизация остатков FBS: cabinetId={}, складов={}, chrtId={}",
                cabinet.getId(), warehouses.size(), chrtIds.size());

        for (SellerWarehouse warehouse : warehouses) {
            if (Boolean.TRUE.equals(warehouse.getIsDeleting())) {
                log.info("Пропуск склада продавца warehouseId={}: удаляется", warehouse.getWarehouseId());
                continue;
            }
            try {
                List<WbFbsStocksResponse.Item> items = fetchAllStocks(apiKey, warehouse.getWarehouseId(), chrtIds);
                self.replaceWarehouseStocks(cabinet, warehouse.getWarehouseId(), items, barcodeByChrtId);
            } catch (HttpClientErrorException.NotFound e) {
                log.warn("Склад продавца warehouseId={} не найден в WB при запросе остатков FBS, cabinetId={}",
                        warehouse.getWarehouseId(), cabinet.getId());
            }
        }
    }

    /**
     * Перезаписывает снимок остатков FBS по одному складу продавца.
     *
     * @param cabinet          кабинет
     * @param warehouseId      ID склада продавца
     * @param items            ответ WB по всем батчам chrtId
     * @param barcodeByChrtId  первый баркод кабинета по chrtId
     */
    @Transactional
    public void replaceWarehouseStocks(
            Cabinet cabinet,
            Long warehouseId,
            List<WbFbsStocksResponse.Item> items,
            Map<Long, ProductBarcode> barcodeByChrtId
    ) {
        Map<Long, WbFbsStocksResponse.Item> byChrtId = new HashMap<>();
        for (WbFbsStocksResponse.Item item : items) {
            if (item == null || item.getChrtId() == null) {
                continue;
            }
            byChrtId.put(item.getChrtId(), item);
        }

        List<ProductFbsStock> existing = fbsStockRepository.findByCabinet_IdAndWarehouseId(cabinet.getId(), warehouseId);
        Map<Long, ProductFbsStock> existingByChrtId = new HashMap<>();
        for (ProductFbsStock stock : existing) {
            existingByChrtId.put(stock.getChrtId(), stock);
        }

        int upserted = 0;
        for (WbFbsStocksResponse.Item item : byChrtId.values()) {
            ProductBarcode barcode = barcodeByChrtId.get(item.getChrtId());
            ProductFbsStock stock = existingByChrtId.remove(item.getChrtId());
            if (stock == null) {
                stock = ProductFbsStock.builder()
                        .cabinet(cabinet)
                        .warehouseId(warehouseId)
                        .chrtId(item.getChrtId())
                        .build();
            }
            stock.setNmId(barcode != null ? barcode.getNmId() : null);
            stock.setSku(resolveSku(item, barcode));
            stock.setAmount(item.getAmount() != null ? item.getAmount() : 0);
            fbsStockRepository.save(stock);
            upserted++;
        }

        if (!existingByChrtId.isEmpty()) {
            fbsStockRepository.deleteAll(existingByChrtId.values());
        }
        log.info("Остатки FBS cabinetId={}, warehouseId={}: сохранено {}, удалено устаревших {}",
                cabinet.getId(), warehouseId, upserted, existingByChrtId.size());
    }

    private List<WbFbsStocksResponse.Item> fetchAllStocks(String apiKey, Long warehouseId, List<Long> chrtIds) {
        List<WbFbsStocksResponse.Item> all = new ArrayList<>();
        for (List<Long> batch : partition(chrtIds, CHRT_BATCH_SIZE)) {
            WbFbsStocksResponse response = fbsApiClient.getFbsStocks(apiKey, warehouseId, batch);
            if (response.getStocks() != null) {
                all.addAll(response.getStocks());
            }
        }
        return all;
    }

    private static List<Long> distinctChrtIds(List<ProductBarcode> barcodes) {
        Set<Long> unique = new HashSet<>();
        List<Long> result = new ArrayList<>();
        for (ProductBarcode barcode : barcodes) {
            if (barcode.getChrtId() != null && unique.add(barcode.getChrtId())) {
                result.add(barcode.getChrtId());
            }
        }
        return result;
    }

    private static Map<Long, ProductBarcode> firstBarcodeByChrtId(List<ProductBarcode> barcodes) {
        Map<Long, ProductBarcode> result = new HashMap<>();
        for (ProductBarcode barcode : barcodes) {
            if (barcode.getChrtId() != null) {
                result.putIfAbsent(barcode.getChrtId(), barcode);
            }
        }
        return result;
    }

    private static String resolveSku(WbFbsStocksResponse.Item item, ProductBarcode barcode) {
        if (item.getSku() != null && !item.getSku().isBlank()) {
            return item.getSku();
        }
        return barcode != null ? barcode.getBarcode() : null;
    }

    private static List<List<Long>> partition(List<Long> values, int batchSize) {
        List<List<Long>> batches = new ArrayList<>();
        for (int i = 0; i < values.size(); i += batchSize) {
            batches.add(new ArrayList<>(values.subList(i, Math.min(i + batchSize, values.size()))));
        }
        return batches;
    }
}
