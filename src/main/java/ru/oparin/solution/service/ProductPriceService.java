package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.wb.ProductPricesResponse;
import ru.oparin.solution.model.ProductPriceHistory;
import ru.oparin.solution.repository.ProductPriceHistoryRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервис для работы с ценами товаров.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductPriceService {

    private final ProductPriceHistoryRepository priceHistoryRepository;

    /**
     * Сохраняет цены товаров за вчерашнюю дату.
     * Если у всех размеров одинаковая цена, сохраняется одна запись с sizeId = null.
     * Если цены различаются, сохраняются отдельные записи для каждого размера.
     */
    @Transactional
    public void savePrices(ProductPricesResponse response, LocalDate yesterdayDate) {
        if (response == null || response.getData() == null || response.getData().getListGoods() == null) {
            log.warn("Получен пустой ответ с ценами товаров");
            return;
        }

        List<ProductPriceHistory> pricesToSave = new ArrayList<>();
        int savedCount = 0;
        int skippedCount = 0;

        for (ProductPricesResponse.Good good : response.getData().getListGoods()) {
            if (!isValidGood(good)) {
                log.warn("Пропущен некорректный товар: nmId={}", good.getNmId());
                skippedCount++;
                continue;
            }

            List<ProductPriceHistory> prices = processGood(good, yesterdayDate);
            pricesToSave.addAll(prices);
        }

        if (!pricesToSave.isEmpty()) {
            priceHistoryRepository.saveAll(pricesToSave);
            savedCount = pricesToSave.size();
            log.info("Сохранено записей цен: {} (пропущено товаров: {})", savedCount, skippedCount);
        } else {
            log.warn("Нет данных для сохранения цен");
        }
    }

    private boolean isValidGood(ProductPricesResponse.Good good) {
        return good != null
                && good.getNmId() != null
                && good.getDiscount() != null
                && good.getClubDiscount() != null
                && good.getSizes() != null
                && !good.getSizes().isEmpty();
    }

    private List<ProductPriceHistory> processGood(ProductPricesResponse.Good good, LocalDate date) {
        List<ProductPriceHistory> prices = new ArrayList<>();

        // Проверяем, одинаковые ли цены у всех размеров
        if (areAllPricesEqual(good.getSizes())) {
            // Сохраняем одну запись с sizeId = null (берем данные из первого размера)
            ProductPriceHistory price = createPriceHistory(good, date, null, null, good.getSizes().get(0));
            prices.add(price);
        } else {
            // Сохраняем отдельные записи для каждого размера
            for (ProductPricesResponse.Size size : good.getSizes()) {
                if (isValidSize(size)) {
                    ProductPriceHistory price = createPriceHistory(good, date, size.getSizeId(), size.getTechSizeName(), size);
                    prices.add(price);
                }
            }
        }

        return prices;
    }

    private boolean areAllPricesEqual(List<ProductPricesResponse.Size> sizes) {
        if (sizes == null || sizes.isEmpty() || sizes.size() == 1) {
            return true;
        }

        ProductPricesResponse.Size firstSize = sizes.get(0);
        if (!isValidSize(firstSize)) {
            return false;
        }

        BigDecimal firstPrice = firstSize.getPrice();
        BigDecimal firstDiscountedPrice = firstSize.getDiscountedPrice();
        BigDecimal firstClubDiscountedPrice = firstSize.getClubDiscountedPrice();

        return sizes.stream().allMatch(size -> {
            if (!isValidSize(size)) {
                return false;
            }
            return compareBigDecimals(firstPrice, size.getPrice())
                    && compareBigDecimals(firstDiscountedPrice, size.getDiscountedPrice())
                    && compareBigDecimals(firstClubDiscountedPrice, size.getClubDiscountedPrice());
        });
    }

    private boolean compareBigDecimals(BigDecimal first, BigDecimal second) {
        if (first == null && second == null) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        return first.compareTo(second) == 0;
    }

    private boolean isValidSize(ProductPricesResponse.Size size) {
        return size != null
                && size.getPrice() != null
                && size.getDiscountedPrice() != null
                && size.getClubDiscountedPrice() != null;
    }

    private ProductPriceHistory createPriceHistory(
            ProductPricesResponse.Good good,
            LocalDate date,
            Long sizeId,
            String techSizeName,
            ProductPricesResponse.Size size
    ) {
        // Все цены уже приходят в рублях (BigDecimal), сохраняем как есть без округления
        log.debug("Сохранение цен для nmId={}: price={}, discountedPrice={}, clubDiscountedPrice={}, sppPrice={} (все в рублях)",
                good.getNmId(),
                size.getPrice(), 
                size.getDiscountedPrice(), 
                size.getClubDiscountedPrice(),
                size.getSppPrice());
        
        // Логируем значение СПП для отладки
        if (size.getSppPrice() != null) {
            log.info("Найдено значение СПП для nmId={}, sizeId={}: sppPrice={}", 
                    good.getNmId(), sizeId, size.getSppPrice());
        } else {
            log.debug("Значение СПП отсутствует (null) для nmId={}, sizeId={}", good.getNmId(), sizeId);
        }
        
            return ProductPriceHistory.builder()
                    .nmId(good.getNmId())
                    .date(date)
                    .sizeId(sizeId)
                    .techSizeName(techSizeName)
                    .price(size.getPrice())
                    .discountedPrice(size.getDiscountedPrice())
                    .clubDiscountedPrice(size.getClubDiscountedPrice())
                    .sppPrice(size.getSppPrice())
                    .discount(good.getDiscount())
                    .clubDiscount(good.getClubDiscount())
                    .editableSizePrice(good.getEditableSizePrice())
                    .isBadTurnover(good.getIsBadTurnover())
                    .build();
    }

    /**
     * Получает текущую цену товара (за вчерашнюю дату).
     */
    public List<ProductPriceHistory> getCurrentPrices(Long nmId, LocalDate yesterdayDate) {
        return priceHistoryRepository.findByNmIdAndDate(nmId, yesterdayDate);
    }

    /**
     * Получает цены для списка товаров за указанную дату.
     */
    public List<ProductPriceHistory> getPricesByNmIdsAndDate(List<Long> nmIds, LocalDate date) {
        return priceHistoryRepository.findByNmIdInAndDate(nmIds, date);
    }
}

