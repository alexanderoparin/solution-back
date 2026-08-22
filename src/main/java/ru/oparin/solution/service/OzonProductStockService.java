package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.ozon.OzonProductInfoStocksResponse;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.OzonProductStock;
import ru.oparin.solution.repository.OzonProductStockRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Сохранение остатков Ozon.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OzonProductStockService {

    private final OzonProductStockRepository stockRepository;

    @Transactional
    public void saveStocks(Cabinet cabinet, OzonProductInfoStocksResponse response) {
        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            return;
        }
        List<OzonProductStock> toSave = new ArrayList<>();
        for (OzonProductInfoStocksResponse.Item item : response.getItems()) {
            if (item.getProductId() == null || item.getStocks() == null) {
                continue;
            }
            for (OzonProductInfoStocksResponse.Stock stock : item.getStocks()) {
                if (stock.getType() == null || stock.getType().isBlank()) {
                    continue;
                }
                Long sku = stock.getSku() != null ? stock.getSku() : 0L;
                String stockType = stock.getType().trim().toLowerCase();
                OzonProductStock row = stockRepository
                        .findByCabinet_IdAndProductIdAndSkuAndStockType(
                                cabinet.getId(), item.getProductId(), sku, stockType)
                        .orElseGet(() -> OzonProductStock.builder()
                                .cabinet(cabinet)
                                .productId(item.getProductId())
                                .sku(sku)
                                .stockType(stockType)
                                .build());
                row.setPresent(stock.getPresent() != null ? stock.getPresent() : 0);
                row.setReserved(stock.getReserved() != null ? stock.getReserved() : 0);
                toSave.add(row);
            }
        }
        if (!toSave.isEmpty()) {
            stockRepository.saveAll(toSave);
            log.info("Ozon остатки cabinetId={}: сохранено/обновлено {}", cabinet.getId(), toSave.size());
        }
    }
}
