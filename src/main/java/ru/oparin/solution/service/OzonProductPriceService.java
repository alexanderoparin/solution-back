package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.ozon.OzonProductInfoPricesResponse;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.OzonProductPriceHistory;
import ru.oparin.solution.repository.OzonProductPriceHistoryRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Сохранение снимков цен Ozon.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OzonProductPriceService {

    private final OzonProductPriceHistoryRepository priceHistoryRepository;

    /**
     * Сохраняет или обновляет цены из ответа API на указанную дату.
     */
    @Transactional
    public void savePrices(Cabinet cabinet, OzonProductInfoPricesResponse response, LocalDate date) {
        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            return;
        }
        List<OzonProductPriceHistory> toSave = new ArrayList<>();
        for (OzonProductInfoPricesResponse.Item item : response.getItems()) {
            if (item.getProductId() == null || item.getPrice() == null || item.getPrice().getPrice() == null) {
                continue;
            }
            OzonProductPriceHistory row = priceHistoryRepository
                    .findByCabinet_IdAndProductIdAndDate(cabinet.getId(), item.getProductId(), date)
                    .orElseGet(() -> OzonProductPriceHistory.builder()
                            .cabinet(cabinet)
                            .productId(item.getProductId())
                            .date(date)
                            .build());
            OzonProductInfoPricesResponse.Price price = item.getPrice();
            row.setPrice(price.getPrice());
            row.setOldPrice(price.getOldPrice());
            row.setMarketingPrice(price.getMarketingPrice());
            row.setMinPrice(price.getMinPrice());
            row.setCurrencyCode(normalizeCurrency(price.getCurrencyCode()));
            toSave.add(row);
        }
        if (!toSave.isEmpty()) {
            priceHistoryRepository.saveAll(toSave);
            log.info("Ozon цены cabinetId={}, date={}: сохранено/обновлено {}", cabinet.getId(), date, toSave.size());
        }
    }

    private static String normalizeCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return "RUB";
        }
        return currencyCode.trim();
    }
}
