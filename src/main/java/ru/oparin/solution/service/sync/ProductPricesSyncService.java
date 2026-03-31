package ru.oparin.solution.service.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import ru.oparin.solution.dto.wb.OrdersResponse;
import ru.oparin.solution.dto.wb.ProductPricesRequest;
import ru.oparin.solution.dto.wb.ProductPricesResponse;
import ru.oparin.solution.exception.WbApiUnauthorizedScopeException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.ProductCard;
import ru.oparin.solution.model.ProductPriceHistory;
import ru.oparin.solution.repository.ProductCardRepository;
import ru.oparin.solution.service.CabinetScopeStatusService;
import ru.oparin.solution.service.ProductPriceService;
import ru.oparin.solution.service.wb.WbApiCategory;
import ru.oparin.solution.service.wb.WbOrdersApiClient;
import ru.oparin.solution.service.wb.WbProductsApiClient;

import java.net.UnknownHostException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Синхронизация цен товаров и СПП (скидка постоянного покупателя) из заказов WB с БД.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductPricesSyncService {

    private static final int PRICES_BATCH_SIZE = 1000;
    @Value("${wb.prices.api-call-delay-ms:600}")
    private int pricesApiCallDelayMs;

    private final ProductCardRepository productCardRepository;
    private final ProductPriceService productPriceService;
    private final WbProductsApiClient productsApiClient;
    private final WbOrdersApiClient ordersApiClient;
    private final CabinetScopeStatusService cabinetScopeStatusService;

    /**
     * Загружает цены товаров кабинета за вчерашнюю дату из WB API и сохраняет в БД.
     * Запросы выполняются батчами с задержкой между вызовами; товары, для которых цены уже есть, пропускаются.
     *
     * @param cabinet кабинет, для которого загружаются цены
     * @param apiKey  API-ключ WB для доступа к discounts-prices-api
     */
    public void updateProductPrices(Cabinet cabinet, String apiKey) {
        LocalDate date = LocalDate.now().minusDays(1);
        log.info("Начало загрузки цен товаров за дату {} для кабинета (ID: {})", date, cabinet.getId());

        try {
            List<Long> nmIds = getDistinctNmIdsForCabinet(cabinet.getId());
            if (nmIds.isEmpty()) {
                log.info("У кабинета (ID: {}) нет карточек товаров для загрузки цен", cabinet.getId());
                return;
            }

            Set<Long> existingNmIds = getExistingNmIdsForDate(nmIds, date, cabinet.getId());
            List<Long> nmIdsToLoad = nmIds.stream()
                    .filter(nmId -> !existingNmIds.contains(nmId))
                    .toList();
            if (nmIdsToLoad.isEmpty()) {
                log.info("Цены за дату {} уже загружены для всех товаров кабинета (ID: {}). Пропускаем.", date, cabinet.getId());
                return;
            }

            log.info("Найдено {} товаров для загрузки цен у кабинета (ID: {}). Уже загружено: {}, требуется: {}",
                    nmIds.size(), cabinet.getId(), existingNmIds.size(), nmIdsToLoad.size());

            loadPricesInBatches(cabinet, apiKey, date, nmIdsToLoad);
            log.info("Завершена загрузка цен товаров за дату {} для кабинета (ID: {})", date, cabinet.getId());
            cabinetScopeStatusService.recordSuccess(cabinet.getId(), WbApiCategory.PRICES_AND_DISCOUNTS);
        } catch (WbApiUnauthorizedScopeException e) {
            handleWbUnauthorizedScope(cabinet, e);
        } catch (ResourceAccessException e) {
            handleResourceAccessError(cabinet, e, "загрузка цен товаров");
        } catch (Exception e) {
            log.error("Ошибка при загрузке цен товаров для кабинета (ID: {}): {}", cabinet.getId(), e.getMessage(), e);
        }
    }

    /**
     * Обновляет СПП (скидку постоянного покупателя) из заказов WB за вчерашнюю дату.
     * Загружает заказы за дату, извлекает значения СПП по артикулам и обновляет историю цен кабинета.
     *
     * @param cabinet кабинет, для которого обновляется СПП
     * @param apiKey  API-ключ WB для доступа к API заказов
     */
    public void updateSppFromOrders(Cabinet cabinet, String apiKey) {
        LocalDate date = LocalDate.now().minusDays(1);
        log.info("Начало обновления СПП из заказов за дату {} для кабинета (ID: {})", date, cabinet.getId());

        try {
            List<OrdersResponse.Order> orders = ordersApiClient.getOrders(apiKey, date, 1);
            if (orders == null || orders.isEmpty()) {
                log.info("Не найдено заказов за дату {} для кабинета (ID: {}). Пропускаем обновление СПП.", date, cabinet.getId());
                return;
            }

            log.info("Получено заказов за дату {}: {} для кабинета (ID: {})", date, orders.size(), cabinet.getId());

            SppExtractionResult sppResult = extractSppByNmIdFromOrders(orders);
            logConflictingSppIfAny(sppResult.sppValuesByNmId(), sppResult.sppLastValueByNmId(), date);

            if (sppResult.sppLastValueByNmId().isEmpty()) {
                log.warn("Не найдено валидных данных СПП в заказах за дату {} для кабинета (ID: {})", date, cabinet.getId());
                return;
            }

            log.info("Найдено {} уникальных артикулов с данными СПП для обновления", sppResult.sppLastValueByNmId().size());
            productPriceService.updateSppDiscount(sppResult.sppLastValueByNmId(), date, cabinet.getId());
            log.info("Завершено обновление СПП из заказов за дату {} для кабинета (ID: {})", date, cabinet.getId());
            cabinetScopeStatusService.recordSuccess(cabinet.getId(), WbApiCategory.STATISTICS);
        } catch (WbApiUnauthorizedScopeException e) {
            handleWbUnauthorizedScope(cabinet, e);
        } catch (Exception e) {
            log.error("Ошибка при обновлении СПП из заказов для кабинета (ID: {}): {}", cabinet.getId(), e.getMessage(), e);
        }
    }

    private List<Long> getDistinctNmIdsForCabinet(Long cabinetId) {
        List<ProductCard> productCards = productCardRepository.findByCabinet_Id(cabinetId);
        return productCards.stream()
                .map(ProductCard::getNmId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private Set<Long> getExistingNmIdsForDate(List<Long> nmIds, LocalDate date, Long cabinetId) {
        List<ProductPriceHistory> existingPrices = productPriceService.getPricesByNmIdsAndDate(nmIds, date, cabinetId);
        return existingPrices.stream()
                .map(ProductPriceHistory::getNmId)
                .collect(Collectors.toSet());
    }

    private void loadPricesInBatches(Cabinet cabinet, String apiKey, LocalDate date, List<Long> nmIdsToLoad) {
        List<List<Long>> batches = partition(nmIdsToLoad, PRICES_BATCH_SIZE);
        log.info("Разбито на {} батчей для загрузки цен", batches.size());

        for (int i = 0; i < batches.size(); i++) {
            List<Long> batch = batches.get(i);
            log.info("Загрузка цен для батча {}/{} ({} товаров)", i + 1, batches.size(), batch.size());

            try {
                ProductPricesRequest request = ProductPricesRequest.builder().nmList(batch).build();
                ProductPricesResponse response = productsApiClient.getProductPrices(apiKey, request);
                productPriceService.savePrices(response, date, cabinet);

                if (i < batches.size() - 1) {
                    SyncDelayUtil.sleep(pricesApiCallDelayMs);
                }
            } catch (WbApiUnauthorizedScopeException e) {
                handleWbUnauthorizedScope(cabinet, e);
                break;
            } catch (ResourceAccessException e) {
                handleResourceAccessError(cabinet, e, "загрузка цен для батча " + (i + 1) + "/" + batches.size());
            } catch (Exception e) {
                log.error("Ошибка при загрузке цен для батча {}/{}: {}", i + 1, batches.size(), e.getMessage(), e);
            }
        }
    }

    private static SppExtractionResult extractSppByNmIdFromOrders(List<OrdersResponse.Order> orders) {
        Map<Long, Integer> sppLastValueByNmId = new HashMap<>();
        Map<Long, Set<Integer>> sppValuesByNmId = new HashMap<>();

        for (OrdersResponse.Order order : orders) {
            if (order.getNmId() != null && order.getSpp() != null) {
                Long nmId = order.getNmId();
                Integer spp = order.getSpp();
                sppValuesByNmId.computeIfAbsent(nmId, k -> new HashSet<>()).add(spp);
                sppLastValueByNmId.put(nmId, spp);
            }
        }

        return new SppExtractionResult(sppLastValueByNmId, sppValuesByNmId);
    }

    private static void logConflictingSppIfAny(
            Map<Long, Set<Integer>> sppValuesByNmId,
            Map<Long, Integer> sppLastValueByNmId,
            LocalDate date) {
        for (Map.Entry<Long, Set<Integer>> entry : sppValuesByNmId.entrySet()) {
            if (entry.getValue().size() > 1) {
                log.warn("Разные значения СПП для товара nmId={} за дату {}: {}. Используется последнее: {}",
                        entry.getKey(), date, entry.getValue(), sppLastValueByNmId.get(entry.getKey()));
            }
        }
    }

    private void handleWbUnauthorizedScope(Cabinet cabinet, WbApiUnauthorizedScopeException e) {
        cabinetScopeStatusService.recordFailure(cabinet.getId(), e.getCategory(), e.getMessage());
        log.warn("Для кабинета {} нет доступа к категории WB API: {}. Проверьте настройки токена в ЛК продавца.",
                cabinet.getId(), e.getCategory().getDisplayName());
    }

    private static void handleResourceAccessError(Cabinet cabinet, ResourceAccessException e, String context) {
        if (e.getCause() instanceof UnknownHostException) {
            log.error("Ошибка при {} для кабинета (ID: {}): не удалось разрешить хост WB API (DNS). Проверьте доступность discounts-prices-api.wildberries.ru и настройки DNS на сервере.",
                    context, cabinet.getId());
        } else {
            log.error("Ошибка при {} для кабинета (ID: {}): {}", context, cabinet.getId(), e.getMessage(), e);
        }
    }

    private static <T> List<List<T>> partition(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            int end = Math.min(i + batchSize, list.size());
            batches.add(new ArrayList<>(list.subList(i, end)));
        }
        return batches;
    }

    private record SppExtractionResult(
            Map<Long, Integer> sppLastValueByNmId,
            Map<Long, Set<Integer>> sppValuesByNmId
    ) {}
}
