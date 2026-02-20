package ru.oparin.solution.service;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.repository.CabinetRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Единая точка входа для полного обновления по всем кабинетам (как ночной шедулер).
 * Только оркестрирует: для каждого кабинета вызывает свой сервис в своей транзакции.
 * Не выполняет бизнес-логику и не открывает транзакции сам.
 */
@Service
@Slf4j
public class FullUpdateOrchestrator {

    private final CabinetRepository cabinetRepository;
    private final ProductCardAnalyticsService productCardAnalyticsService;
    private final PromotionCalendarService promotionCalendarService;
    private final Executor cabinetUpdateExecutor;

    public FullUpdateOrchestrator(CabinetRepository cabinetRepository,
                                  ProductCardAnalyticsService productCardAnalyticsService,
                                  PromotionCalendarService promotionCalendarService,
                                  @Qualifier("cabinetUpdateExecutor") Executor cabinetUpdateExecutor) {
        this.cabinetRepository = cabinetRepository;
        this.productCardAnalyticsService = productCardAnalyticsService;
        this.promotionCalendarService = promotionCalendarService;
        this.cabinetUpdateExecutor = cabinetUpdateExecutor;
    }

    /**
     * Полное обновление по всем кабинетам с API-ключом: для каждого кабинета — обновление аналитики (своя транзакция),
     * затем синхронизация акций календаря (своя транзакция). Кабинеты обрабатываются параллельно.
     */
    public void runFullUpdate() {
        List<Cabinet> cabinets = cabinetRepository.findCabinetsWithApiKeyAndUser(Role.SELLER);
        log.info("Запуск полного обновления по кабинетам. Найдено кабинетов с API-ключом: {}", cabinets.size());

        if (cabinets.isEmpty()) {
            log.info("Кабинетов с ключом не найдено, обновление пропущено");
            return;
        }

        LocalDate to = LocalDate.now().minusDays(1);
        LocalDate from = to.minusDays(13);
        log.info("Период для загрузки аналитики: {} - {}", from, to);

        List<CompletableFuture<Void>> futures = cabinets.stream()
                .map(cabinet -> CompletableFuture.runAsync(() -> {
                    String prevName = Thread.currentThread().getName();
                    try {
                        Thread.currentThread().setName("full-update-cabinet-" + cabinet.getId());
                        MDC.put("cabinetId", String.valueOf(cabinet.getId()));
                        productCardAnalyticsService.updateCabinetAnalyticsInTransaction(cabinet, from, to);
                        promotionCalendarService.syncPromotionsForCabinet(cabinet);
                    } catch (Exception e) {
                        log.error("Ошибка при полном обновлении кабинета (ID: {}, продавец: {}): {}",
                                cabinet.getId(), cabinet.getUser().getEmail(), e.getMessage());
                    } finally {
                        Thread.currentThread().setName(prevName);
                        MDC.remove("cabinetId");
                    }
                }, cabinetUpdateExecutor))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        log.info("Завершено полное обновление по {} кабинетам.", cabinets.size());
    }
}
