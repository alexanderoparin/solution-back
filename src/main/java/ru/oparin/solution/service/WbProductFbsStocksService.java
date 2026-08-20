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
import ru.oparin.solution.model.WbProductBarcode;
import ru.oparin.solution.model.WbProductFbsStock;
import ru.oparin.solution.model.WbSellerWarehouse;
import ru.oparin.solution.repository.WbProductBarcodeRepository;
import ru.oparin.solution.repository.WbProductFbsStockRepository;
import ru.oparin.solution.service.wb.WbFbsApiClient;

import java.util.*;

/**
 * Синхронизация остатков FBS: POST /api/v3/stocks/{warehouseId} пачками по 1000 chrtId.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WbProductFbsStocksService {

    /**
     * Лимит WB: не более 1000 chrtId в одном запросе остатков.
     */
    private static final int CHRT_BATCH_SIZE = 1000;

    private final WbFbsApiClient fbsApiClient;
    private final WbSellerWarehouseService sellerWarehouseService;
    private final WbProductBarcodeRepository barcodeRepository;
    private final WbProductFbsStockRepository fbsStockRepository;

    /**
     * self-proxy, чтобы {@code @Transactional} на сохранении снимка не обходился самовызовом.
     */
    @Lazy
    @Autowired
    private WbProductFbsStocksService self;

    /**
     * Запрашивает остатки FBS по всем складам продавца кабинета и сохраняет снимок.
     *
     * @param apiKey  токен с категорией «Маркетплейс»
     * @param cabinet кабинет
     */
    public void syncCabinet(String apiKey, Cabinet cabinet) {
        List<WbSellerWarehouse> warehouses = sellerWarehouseService.findByCabinetId(cabinet.getId());
        if (warehouses.isEmpty()) {
            log.info("Нет складов продавца для синхронизации остатков FBS, cabinetId={}", cabinet.getId());
            return;
        }

        List<WbProductBarcode> barcodes = barcodeRepository.findByCabinet_Id(cabinet.getId());
        List<Long> chrtIds = distinctChrtIds(barcodes);
        if (chrtIds.isEmpty()) {
            log.info("Нет chrtId в wb_product_barcodes для остатков FBS, cabinetId={}", cabinet.getId());
            return;
        }

        Map<Long, WbProductBarcode> barcodeByChrtId = firstBarcodeByChrtId(barcodes);
        log.info("Синхронизация остатков FBS: cabinetId={}, складов={}, chrtId={}",
                cabinet.getId(), warehouses.size(), chrtIds.size());

        for (WbSellerWarehouse warehouse : warehouses) {
            if (Boolean.TRUE.equals(warehouse.getIsDeleting())) {
                log.info("Пропуск склада продавца warehouseId={}: удаляется", warehouse.getWarehouseId());
                continue;
            }
            try {
                List<WbFbsStocksResponse.Item> items = fetchAllStocks(apiKey, warehouse.getWarehouseId(), chrtIds);
                items = withMissingChrtAsZero(items, chrtIds);
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
            Map<Long, WbProductBarcode> barcodeByChrtId
    ) {
        Map<Long, WbFbsStocksResponse.Item> byChrtId = new HashMap<>();
        for (WbFbsStocksResponse.Item item : items) {
            if (item == null || item.getChrtId() == null) {
                continue;
            }
            byChrtId.put(item.getChrtId(), item);
        }

        List<WbProductFbsStock> existing = fbsStockRepository.findByCabinet_IdAndWarehouseId(cabinet.getId(), warehouseId);
        Map<Long, WbProductFbsStock> existingByChrtId = new HashMap<>();
        for (WbProductFbsStock stock : existing) {
            existingByChrtId.put(stock.getChrtId(), stock);
        }

        int upserted = 0;
        for (WbFbsStocksResponse.Item item : byChrtId.values()) {
            WbProductBarcode barcode = barcodeByChrtId.get(item.getChrtId());
            WbProductFbsStock stock = existingByChrtId.remove(item.getChrtId());
            if (stock == null) {
                stock = WbProductFbsStock.builder()
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

    /**
     * WB не возвращает chrtId без записи остатка. Для снимка по артикулу дописываем amount=0.
     */
    private static List<WbFbsStocksResponse.Item> withMissingChrtAsZero(
            List<WbFbsStocksResponse.Item> items,
            List<Long> requestedChrtIds
    ) {
        Set<Long> returned = new HashSet<>();
        for (WbFbsStocksResponse.Item item : items) {
            if (item != null && item.getChrtId() != null) {
                returned.add(item.getChrtId());
            }
        }
        List<WbFbsStocksResponse.Item> result = new ArrayList<>(items);
        for (Long chrtId : requestedChrtIds) {
            if (returned.add(chrtId)) {
                result.add(WbFbsStocksResponse.Item.builder()
                        .chrtId(chrtId)
                        .amount(0)
                        .build());
            }
        }
        return result;
    }

    private static List<Long> distinctChrtIds(List<WbProductBarcode> barcodes) {
        Set<Long> unique = new HashSet<>();
        List<Long> result = new ArrayList<>();
        for (WbProductBarcode barcode : barcodes) {
            if (barcode.getChrtId() != null && unique.add(barcode.getChrtId())) {
                result.add(barcode.getChrtId());
            }
        }
        return result;
    }

    private static Map<Long, WbProductBarcode> firstBarcodeByChrtId(List<WbProductBarcode> barcodes) {
        Map<Long, WbProductBarcode> result = new HashMap<>();
        for (WbProductBarcode barcode : barcodes) {
            if (barcode.getChrtId() != null) {
                result.putIfAbsent(barcode.getChrtId(), barcode);
            }
        }
        return result;
    }

    private static String resolveSku(WbFbsStocksResponse.Item item, WbProductBarcode barcode) {
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
