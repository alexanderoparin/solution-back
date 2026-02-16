package ru.oparin.solution.service.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.oparin.solution.dto.wb.OrdersResponse;
import ru.oparin.solution.dto.wb.ProductPricesRequest;
import ru.oparin.solution.dto.wb.ProductPricesResponse;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.ProductCard;
import ru.oparin.solution.model.ProductPriceHistory;
import ru.oparin.solution.repository.ProductCardRepository;
import ru.oparin.solution.service.ProductPriceService;
import ru.oparin.solution.service.wb.WbOrdersApiClient;
import ru.oparin.solution.service.wb.WbProductsApiClient;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Синхронизация цен товаров и СПП из заказов WB с БД.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductPricesSyncService {

    private static final int PRICES_BATCH_SIZE = 1000;
    private static final int PRICES_API_CALL_DELAY_MS = 600;

    private final ProductCardRepository productCardRepository;
    private final ProductPriceService productPriceService;
    private final WbProductsApiClient productsApiClient;
    private final WbOrdersApiClient ordersApiClient;

    /**
     * Загружает цены товаров кабинета за вчерашнюю дату (батчами, с задержкой между запросами).
     */
    public void updateProductPrices(Cabinet cabinet, String apiKey) {
        try {
            LocalDate yesterdayDate = LocalDate.now().minusDays(1);
            log.info("Начало загрузки цен товаров за дату {} для кабинета (ID: {})", yesterdayDate, cabinet.getId());

            List<ProductCard> productCards = productCardRepository.findByCabinet_Id(cabinet.getId());
            if (productCards.isEmpty()) {
                log.info("У кабинета (ID: {}) нет карточек товаров для загрузки цен", cabinet.getId());
                return;
            }

            List<Long> nmIds = productCards.stream()
                    .map(ProductCard::getNmId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            if (nmIds.isEmpty()) {
                log.warn("Не найдено валидных nmId для загрузки цен у кабинета (ID: {})", cabinet.getId());
                return;
            }

            List<ProductPriceHistory> existingPrices = productPriceService.getPricesByNmIdsAndDate(nmIds, yesterdayDate, cabinet.getId());
            Set<Long> existingNmIds = existingPrices.stream()
                    .map(ProductPriceHistory::getNmId)
                    .collect(Collectors.toSet());

            if (existingNmIds.size() == nmIds.size()) {
                log.info("Цены за дату {} уже загружены для всех {} товаров кабинета (ID: {}). Пропускаем.",
                        yesterdayDate, nmIds.size(), cabinet.getId());
                return;
            }

            List<Long> nmIdsToLoad = nmIds.stream()
                    .filter(nmId -> !existingNmIds.contains(nmId))
                    .collect(Collectors.toList());
            if (nmIdsToLoad.isEmpty()) {
                log.info("Цены за дату {} уже загружены для всех товаров кабинета (ID: {}). Пропускаем.", yesterdayDate, cabinet.getId());
                return;
            }

            log.info("Найдено {} товаров для загрузки цен у кабинета (ID: {}). Уже загружено: {}, требуется: {}",
                    nmIds.size(), cabinet.getId(), existingNmIds.size(), nmIdsToLoad.size());

            List<List<Long>> batches = partitionList(nmIdsToLoad, PRICES_BATCH_SIZE);
            log.info("Разбито на {} батчей для загрузки цен", batches.size());

            for (int i = 0; i < batches.size(); i++) {
                List<Long> batch = batches.get(i);
                log.info("Загрузка цен для батча {}/{} ({} товаров)", i + 1, batches.size(), batch.size());
                try {
                    ProductPricesRequest request = ProductPricesRequest.builder().nmList(batch).build();
                    ProductPricesResponse response = productsApiClient.getProductPrices(apiKey, request);
                    productPriceService.savePrices(response, yesterdayDate, cabinet);
                    if (i < batches.size() - 1) {
                        SyncDelayUtil.sleep(PRICES_API_CALL_DELAY_MS);
                    }
                } catch (Exception e) {
                    log.error("Ошибка при загрузке цен для батча {}/{}: {}", i + 1, batches.size(), e.getMessage(), e);
                }
            }

            log.info("Завершена загрузка цен товаров за дату {} для кабинета (ID: {})", yesterdayDate, cabinet.getId());
        } catch (Exception e) {
            log.error("Ошибка при загрузке цен товаров для кабинета (ID: {}): {}", cabinet.getId(), e.getMessage(), e);
        }
    }

    /**
     * Обновляет СПП (скидка постоянного покупателя) из заказов за вчерашнюю дату.
     */
    public void updateSppFromOrders(Cabinet cabinet, String apiKey) {
        try {
            LocalDate yesterdayDate = LocalDate.now().minusDays(1);
            log.info("Начало обновления СПП из заказов за дату {} для кабинета (ID: {})", yesterdayDate, cabinet.getId());

            List<OrdersResponse.Order> orders = ordersApiClient.getOrders(apiKey, yesterdayDate, 1);
            if (orders == null || orders.isEmpty()) {
                log.info("Не найдено заказов за дату {} для кабинета (ID: {}). Пропускаем обновление СПП.", yesterdayDate, cabinet.getId());
                return;
            }

            log.info("Получено заказов за дату {}: {} для кабинета (ID: {})", yesterdayDate, orders.size(), cabinet.getId());

            Map<Long, Integer> sppByNmId = new HashMap<>();
            Map<Long, Set<Integer>> sppValuesByNmId = new HashMap<>();

            for (OrdersResponse.Order order : orders) {
                if (order.getNmId() != null && order.getSpp() != null) {
                    Long nmId = order.getNmId();
                    Integer spp = order.getSpp();
                    sppValuesByNmId.computeIfAbsent(nmId, k -> new HashSet<>()).add(spp);
                    sppByNmId.put(nmId, spp);
                }
            }

            for (Map.Entry<Long, Set<Integer>> entry : sppValuesByNmId.entrySet()) {
                if (entry.getValue().size() > 1) {
                    log.warn("Разные значения СПП для товара nmId={} за дату {}: {}. Используется последнее: {}",
                            entry.getKey(), yesterdayDate, entry.getValue(), sppByNmId.get(entry.getKey()));
                }
            }

            if (sppByNmId.isEmpty()) {
                log.warn("Не найдено валидных данных СПП в заказах за дату {} для кабинета (ID: {})", yesterdayDate, cabinet.getId());
                return;
            }

            log.info("Найдено {} уникальных артикулов с данными СПП для обновления", sppByNmId.size());
            productPriceService.updateSppDiscount(sppByNmId, yesterdayDate, cabinet.getId());
            log.info("Завершено обновление СПП из заказов за дату {} для кабинета (ID: {})", yesterdayDate, cabinet.getId());
        } catch (Exception e) {
            log.error("Ошибка при обновлении СПП из заказов для кабинета (ID: {}): {}", cabinet.getId(), e.getMessage(), e);
        }
    }

    private static <T> List<List<T>> partitionList(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            int end = Math.min(i + batchSize, list.size());
            batches.add(new ArrayList<>(list.subList(i, end)));
        }
        return batches;
    }
}
