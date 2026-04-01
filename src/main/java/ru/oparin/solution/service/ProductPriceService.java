package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.wb.ProductPricesResponse;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.ProductPriceHistory;
import ru.oparin.solution.repository.ProductPriceHistoryRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Сервис для работы с ценами товаров.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductPriceService {

    private final ProductPriceHistoryRepository priceHistoryRepository;

    /**
     * Сохраняет цены товаров за дату для кабинета.
     */
    @Transactional
    public void savePrices(ProductPricesResponse response, LocalDate yesterdayDate, Cabinet cabinet) {
        if (response == null || response.getData() == null || response.getData().getListGoods() == null) {
            log.warn("Получен пустой ответ с ценами товаров");
            return;
        }

        List<ProductPriceHistory> built = new ArrayList<>();
        int skippedCount = 0;

        for (ProductPricesResponse.Good good : response.getData().getListGoods()) {
            if (!isValidGood(good)) {
                log.warn("Пропущен некорректный товар: nmId={}", good.getNmId());
                skippedCount++;
                continue;
            }

            built.addAll(processGood(good, yesterdayDate, cabinet));
        }

        if (built.isEmpty()) {
            log.warn("Нет данных для сохранения цен");
            return;
        }

        Map<NmSizeKey, ProductPriceHistory> incomingByKey = dedupeByNmAndSize(built);
        List<Long> nmIds = incomingByKey.keySet().stream().map(NmSizeKey::nmId).distinct().toList();
        Map<NmSizeKey, ProductPriceHistory> existingByKey = loadExistingPriceMap(nmIds, yesterdayDate, cabinet.getId());

        List<ProductPriceHistory> toPersist = new ArrayList<>();
        for (ProductPriceHistory incoming : incomingByKey.values()) {
            NmSizeKey key = NmSizeKey.of(incoming);
            ProductPriceHistory row = existingByKey.get(key);
            if (row != null) {
                applyPriceFieldsFromApi(row, incoming);
                toPersist.add(row);
            } else {
                toPersist.add(incoming);
            }
        }

        priceHistoryRepository.saveAll(toPersist);
        log.info("Сохранено записей цен: {} (уникальных ключей nm+size: {}, пропущено товаров: {})",
                toPersist.size(), incomingByKey.size(), skippedCount);
    }

    private static Map<NmSizeKey, ProductPriceHistory> dedupeByNmAndSize(List<ProductPriceHistory> rows) {
        Map<NmSizeKey, ProductPriceHistory> map = new LinkedHashMap<>();
        for (ProductPriceHistory row : rows) {
            map.put(NmSizeKey.of(row), row);
        }
        return map;
    }

    private Map<NmSizeKey, ProductPriceHistory> loadExistingPriceMap(List<Long> nmIds, LocalDate date, Long cabinetId) {
        List<ProductPriceHistory> existing = priceHistoryRepository.findByNmIdInAndDateAndCabinet_Id(nmIds, date, cabinetId);
        Map<NmSizeKey, ProductPriceHistory> map = new HashMap<>();
        for (ProductPriceHistory row : existing) {
            map.put(NmSizeKey.of(row), row);
        }
        return map;
    }

    /**
     * Обновляет поля снимка цены из WB; sppDiscount не трогаем (его заполняет синхронизация заказов).
     */
    private static void applyPriceFieldsFromApi(ProductPriceHistory target, ProductPriceHistory source) {
        target.setTechSizeName(source.getTechSizeName());
        target.setPrice(source.getPrice());
        target.setDiscountedPrice(source.getDiscountedPrice());
        target.setClubDiscountedPrice(source.getClubDiscountedPrice());
        target.setDiscount(source.getDiscount());
        target.setClubDiscount(source.getClubDiscount());
        target.setEditableSizePrice(source.getEditableSizePrice());
    }

    private record NmSizeKey(Long nmId, Long sizeId) {
        static NmSizeKey of(ProductPriceHistory p) {
            return new NmSizeKey(p.getNmId(), p.getSizeId());
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

    private List<ProductPriceHistory> processGood(ProductPricesResponse.Good good, LocalDate date, Cabinet cabinet) {
        List<ProductPriceHistory> prices = new ArrayList<>();

        if (areAllPricesEqual(good.getSizes())) {
            ProductPriceHistory price = createPriceHistory(good, date, null, null, good.getSizes().get(0), cabinet);
            prices.add(price);
        } else {
            for (ProductPricesResponse.Size size : good.getSizes()) {
                if (isValidSize(size)) {
                    ProductPriceHistory price = createPriceHistory(good, date, size.getSizeId(), size.getTechSizeName(), size, cabinet);
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
            ProductPricesResponse.Size size,
            Cabinet cabinet
    ) {
        return ProductPriceHistory.builder()
                .nmId(good.getNmId())
                .cabinet(cabinet)
                .date(date)
                .sizeId(sizeId)
                .techSizeName(techSizeName)
                .price(size.getPrice())
                .discountedPrice(size.getDiscountedPrice())
                .clubDiscountedPrice(size.getClubDiscountedPrice())
                .discount(good.getDiscount())
                .clubDiscount(good.getClubDiscount())
                .sppDiscount(null)
                .editableSizePrice(good.getEditableSizePrice())
                .build();
    }

    /**
     * Получает текущую цену товара (за вчерашнюю дату).
     */
    public List<ProductPriceHistory> getCurrentPrices(Long nmId, LocalDate yesterdayDate) {
        return priceHistoryRepository.findByNmIdAndDate(nmId, yesterdayDate);
    }

    /**
     * Получает цены для списка товаров за указанную дату по кабинету.
     */
    public List<ProductPriceHistory> getPricesByNmIdsAndDate(List<Long> nmIds, LocalDate date, Long cabinetId) {
        return priceHistoryRepository.findByNmIdInAndDateAndCabinet_Id(nmIds, date, cabinetId);
    }

    /**
     * Получает цены для списка товаров за дату (без фильтра по кабинету, для обратной совместимости).
     */
    public List<ProductPriceHistory> getPricesByNmIdsAndDate(List<Long> nmIds, LocalDate date) {
        return priceHistoryRepository.findByNmIdInAndDate(nmIds, date);
    }

    /**
     * Обновляет поле sppDiscount для записей цен за указанную дату на основе данных из заказов.
     * 
     * @param sppByNmId Map, где ключ - nmId, значение - spp (скидка СПП в процентах)
     * @param date дата, за которую нужно обновить цены
     */
    @Transactional
    public void updateSppDiscount(java.util.Map<Long, Integer> sppByNmId, LocalDate date) {
        updateSppDiscount(sppByNmId, date, null);
    }

    @Transactional
    public void updateSppDiscount(java.util.Map<Long, Integer> sppByNmId, LocalDate date, Long cabinetId) {
        if (sppByNmId == null || sppByNmId.isEmpty()) {
            log.warn("Нет данных СПП для обновления за дату {}", date);
            return;
        }

        int updatedCount = 0;
        for (java.util.Map.Entry<Long, Integer> entry : sppByNmId.entrySet()) {
            Long nmId = entry.getKey();
            Integer sppDiscount = entry.getValue();

            if (nmId == null || sppDiscount == null) {
                continue;
            }

            List<ProductPriceHistory> prices = cabinetId != null
                    ? priceHistoryRepository.findByNmIdAndDateAndCabinet_Id(nmId, date, cabinetId)
                    : priceHistoryRepository.findByNmIdAndDate(nmId, date);
            for (ProductPriceHistory price : prices) {
                price.setSppDiscount(sppDiscount);
                updatedCount++;
            }
        }

        if (updatedCount > 0) {
            priceHistoryRepository.flush();
            log.info("Обновлено записей цен с СПП за дату {}: {}", date, updatedCount);
        } else {
            log.warn("Не найдено записей цен для обновления СПП за дату {}", date);
        }
    }
}

