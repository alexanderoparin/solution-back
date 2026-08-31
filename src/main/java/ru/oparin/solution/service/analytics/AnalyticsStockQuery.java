package ru.oparin.solution.service.analytics;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.StockDto;
import ru.oparin.solution.dto.analytics.StockSizeDto;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Остатки WB: склады FBO/FBS и детализация по размерам.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsStockQuery {

    private final WbProductStockRepository stockRepository;
    private final WbProductFbsStockRepository fbsStockRepository;
    private final WbProductBarcodeRepository barcodeRepository;
    private final WbWarehouseRepository warehouseRepository;
    private final WbSellerWarehouseRepository sellerWarehouseRepository;

    /**
     * Остатки FBO артикула по складам WB.
     */
    @Transactional(readOnly = true)
    public List<StockDto> getStocks(Long nmId, Long cabinetId) {
        List<WbProductStock> stocks = cabinetId != null
                ? stockRepository.findByNmIdAndCabinet_Id(nmId, cabinetId)
                : stockRepository.findByNmId(nmId);

        if (stocks.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, StockAggregate> stockByWarehouse = stocks.stream()
                .collect(Collectors.groupingBy(
                        WbProductStock::getWarehouseId,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                stockList -> {
                                    int totalAmount = stockList.stream()
                                            .mapToInt(WbProductStock::getAmount)
                                            .sum();
                                    LocalDateTime latestUpdate = stockList.stream()
                                            .map(WbProductStock::getUpdatedAt)
                                            .max(LocalDateTime::compareTo)
                                            .orElse(null);
                                    return new StockAggregate(totalAmount, latestUpdate);
                                }
                        )
                ));

        Map<Long, WbWarehouse> warehousesById = warehouseRepository.findAll().stream()
                .collect(Collectors.toMap(
                        w -> Long.valueOf(w.getId()),
                        w -> w,
                        (existing, replacement) -> existing
                ));

        return stockByWarehouse.entrySet().stream()
                .map(entry -> {
                    Long warehouseId = entry.getKey();
                    StockAggregate aggregate = entry.getValue();
                    WbWarehouse warehouse = warehousesById.get(warehouseId);
                    String warehouseName = warehouse != null ? warehouse.getName() : "Склад " + warehouseId;
                    boolean onFire = warehouse != null && Boolean.TRUE.equals(warehouse.getOnFire());

                    return StockDto.builder()
                            .warehouseId(warehouseId)
                            .warehouseName(warehouseName)
                            .onFire(onFire)
                            .amount(aggregate.getTotalAmount())
                            .updatedAt(aggregate.getLatestUpdate())
                            .build();
                })
                .sorted((a, b) -> b.getAmount().compareTo(a.getAmount()))
                .collect(Collectors.toList());
    }

    /**
     * Остатки FBS артикула на складах продавца кабинета.
     */
    @Transactional(readOnly = true)
    public List<StockDto> getFbsStocks(Long nmId, Long cabinetId) {
        if (cabinetId == null) {
            return Collections.emptyList();
        }
        List<WbSellerWarehouse> warehouses = sellerWarehouseRepository.findByCabinet_Id(cabinetId).stream()
                .filter(warehouse -> !Boolean.TRUE.equals(warehouse.getIsDeleting()))
                .toList();
        if (warehouses.isEmpty()) {
            return Collections.emptyList();
        }

        List<WbProductFbsStock> stocks = fbsStockRepository.findByNmIdAndCabinet_Id(nmId, cabinetId);
        Map<Long, StockAggregate> stockByWarehouse = stocks.stream()
                .collect(Collectors.groupingBy(
                        WbProductFbsStock::getWarehouseId,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                stockList -> {
                                    int totalAmount = stockList.stream()
                                            .mapToInt(WbProductFbsStock::getAmount)
                                            .sum();
                                    LocalDateTime latestUpdate = stockList.stream()
                                            .map(WbProductFbsStock::getUpdatedAt)
                                            .max(LocalDateTime::compareTo)
                                            .orElse(null);
                                    return new StockAggregate(totalAmount, latestUpdate);
                                }
                        )
                ));

        return warehouses.stream()
                .map(warehouse -> {
                    StockAggregate aggregate = stockByWarehouse.get(warehouse.getWarehouseId());
                    int amount = aggregate != null ? aggregate.getTotalAmount() : 0;
                    LocalDateTime updatedAt = aggregate != null && aggregate.getLatestUpdate() != null
                            ? aggregate.getLatestUpdate()
                            : warehouse.getUpdatedAt();
                    return StockDto.builder()
                            .warehouseId(warehouse.getWarehouseId())
                            .warehouseName(warehouse.getName())
                            .onFire(false)
                            .amount(amount)
                            .updatedAt(updatedAt)
                            .build();
                })
                .sorted((a, b) -> b.getAmount().compareTo(a.getAmount()))
                .collect(Collectors.toList());
    }

    /**
     * Детализация остатков FBO по размерам на складе WB.
     */
    @Transactional(readOnly = true)
    public List<StockSizeDto> getStockSizes(Long nmId, String warehouseName, Long warehouseId, Long cabinetId) {
        Long resolvedWarehouseId = warehouseId;
        if (resolvedWarehouseId == null) {
            resolvedWarehouseId = warehouseRepository.findAll().stream()
                    .filter(w -> w.getName().equals(warehouseName))
                    .map(w -> Long.valueOf(w.getId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Склад не найден: " + warehouseName));
        }

        List<WbProductStock> stocks = cabinetId != null
                ? stockRepository.findByNmIdAndWarehouseIdAndCabinet_Id(nmId, resolvedWarehouseId, cabinetId)
                : stockRepository.findByNmIdAndWarehouseId(nmId, resolvedWarehouseId);

        List<WbProductBarcode> barcodes = cabinetId != null
                ? barcodeRepository.findByNmIdAndCabinet_Id(nmId, cabinetId)
                : barcodeRepository.findByNmId(nmId);
        Map<String, WbProductBarcode> barcodeMap = barcodes.stream()
                .collect(Collectors.toMap(
                        WbProductBarcode::getBarcode,
                        b -> b,
                        (existing, replacement) -> existing
                ));

        Map<String, StockSizeAggregate> allSizes = seedSizesFromBarcodes(barcodeMap.values());
        for (WbProductStock stock : stocks) {
            WbProductBarcode barcode = barcodeMap.get(stock.getBarcode());
            if (barcode == null) {
                continue;
            }
            addSizeAmount(allSizes, barcode, stock.getAmount());
        }
        return toStockSizeDtos(allSizes);
    }

    /**
     * Детализация остатков FBS по размерам на складе продавца.
     */
    @Transactional(readOnly = true)
    public List<StockSizeDto> getFbsStockSizes(Long nmId, String warehouseName, Long warehouseId, Long cabinetId) {
        if (cabinetId == null) {
            return Collections.emptyList();
        }
        Long resolvedWarehouseId = warehouseId;
        if (resolvedWarehouseId == null) {
            resolvedWarehouseId = sellerWarehouseRepository.findByCabinet_Id(cabinetId).stream()
                    .filter(w -> w.getName().equals(warehouseName))
                    .map(WbSellerWarehouse::getWarehouseId)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Склад продавца не найден: " + warehouseName));
        }

        List<WbProductFbsStock> stocks = fbsStockRepository.findByNmIdAndCabinet_IdAndWarehouseId(
                nmId, cabinetId, resolvedWarehouseId);
        List<WbProductBarcode> barcodes = barcodeRepository.findByNmIdAndCabinet_Id(nmId, cabinetId);
        Map<Long, WbProductBarcode> barcodeByChrtId = new HashMap<>();
        for (WbProductBarcode barcode : barcodes) {
            if (barcode.getChrtId() != null) {
                barcodeByChrtId.putIfAbsent(barcode.getChrtId(), barcode);
            }
        }

        Map<String, StockSizeAggregate> allSizes = seedSizesFromBarcodes(barcodes);
        for (WbProductFbsStock stock : stocks) {
            WbProductBarcode barcode = barcodeByChrtId.get(stock.getChrtId());
            if (barcode == null) {
                continue;
            }
            addSizeAmount(allSizes, barcode, stock.getAmount());
        }
        return toStockSizeDtos(allSizes);
    }

    private Map<String, StockSizeAggregate> seedSizesFromBarcodes(Iterable<WbProductBarcode> barcodes) {
        Map<String, StockSizeAggregate> allSizes = new HashMap<>();
        for (WbProductBarcode barcode : barcodes) {
            String sizeKey = sizeKey(barcode);
            if (!allSizes.containsKey(sizeKey)) {
                allSizes.put(sizeKey, new StockSizeAggregate(barcode.getTechSize(), barcode.getWbSize(), 0));
            }
        }
        return allSizes;
    }

    private void addSizeAmount(Map<String, StockSizeAggregate> allSizes, WbProductBarcode barcode, int amount) {
        String sizeKey = sizeKey(barcode);
        StockSizeAggregate agg = allSizes.get(sizeKey);
        if (agg != null) {
            agg.setAmount(agg.getAmount() + amount);
        } else {
            allSizes.put(sizeKey, new StockSizeAggregate(barcode.getTechSize(), barcode.getWbSize(), amount));
        }
    }

    private static String sizeKey(WbProductBarcode barcode) {
        if (barcode.getWbSize() != null && !barcode.getWbSize().isEmpty()) {
            return barcode.getWbSize();
        }
        return barcode.getTechSize() != null ? barcode.getTechSize() : "Неизвестно";
    }

    private List<StockSizeDto> toStockSizeDtos(Map<String, StockSizeAggregate> allSizes) {
        return allSizes.values().stream()
                .map(agg -> StockSizeDto.builder()
                        .techSize(agg.getTechSize())
                        .wbSize(agg.getWbSize())
                        .amount(agg.getAmount())
                        .build())
                .sorted((a, b) -> {
                    if (a.getWbSize() != null && b.getWbSize() != null) {
                        try {
                            return Integer.compare(Integer.parseInt(a.getWbSize()), Integer.parseInt(b.getWbSize()));
                        } catch (NumberFormatException e) {
                            return a.getWbSize().compareTo(b.getWbSize());
                        }
                    }
                    String aSize = a.getTechSize() != null ? a.getTechSize() : "";
                    String bSize = b.getTechSize() != null ? b.getTechSize() : "";
                    return aSize.compareTo(bSize);
                })
                .collect(Collectors.toList());
    }

    @Value
    private static class StockAggregate {
        int totalAmount;
        LocalDateTime latestUpdate;
    }

    @Getter
    @Setter
    private static class StockSizeAggregate {
        String techSize;
        String wbSize;
        int amount;

        StockSizeAggregate(String techSize, String wbSize, int amount) {
            this.techSize = techSize;
            this.wbSize = wbSize;
            this.amount = amount;
        }
    }
}
