package ru.oparin.solution.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.wb.WbWarehouseResponse;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.CabinetRepository;
import ru.oparin.solution.service.ProductCardAnalyticsService;
import ru.oparin.solution.service.WbApiKeyService;
import ru.oparin.solution.service.WbWarehouseService;
import ru.oparin.solution.service.wb.WbWarehousesApiClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Планировщик задач для автоматической загрузки аналитики.
 */
@Component
@Slf4j
public class AnalyticsScheduler {

    private final WbApiKeyService wbApiKeyService;
    private final CabinetRepository cabinetRepository;
    private final ProductCardAnalyticsService analyticsService;
    private final WbWarehousesApiClient warehousesApiClient;
    private final WbWarehouseService warehouseService;
    private final Executor cabinetUpdateExecutor;

    public AnalyticsScheduler(WbApiKeyService wbApiKeyService,
                             CabinetRepository cabinetRepository,
                             ProductCardAnalyticsService analyticsService,
                             WbWarehousesApiClient warehousesApiClient,
                             WbWarehouseService warehouseService,
                             @Qualifier("cabinetUpdateExecutor") Executor cabinetUpdateExecutor) {
        this.wbApiKeyService = wbApiKeyService;
        this.cabinetRepository = cabinetRepository;
        this.analyticsService = analyticsService;
        this.warehousesApiClient = warehousesApiClient;
        this.warehouseService = warehouseService;
        this.cabinetUpdateExecutor = cabinetUpdateExecutor;
    }

    /**
     * Автоматическая загрузка аналитики для всех кабинетов с привязанным API-ключом (активные продавцы).
     * Запускается каждый день в 01:30. Для каждого кабинета загружаются карточки, кампании и аналитика.
     * Период: последние 14 дней (без текущих суток).
     */
    @Scheduled(cron = "0 30 1 * * ?")
    public void loadAnalyticsForAllActiveSellers() {
        log.info("Запуск автоматической загрузки аналитики по кабинетам");

        List<Cabinet> cabinetsWithKey = findCabinetsWithApiKey();
        log.info("Найдено кабинетов с API-ключом: {}", cabinetsWithKey.size());

        if (cabinetsWithKey.isEmpty()) {
            log.info("Кабинетов с ключом не найдено, загрузка аналитики пропущена");
            return;
        }

        DateRange period = calculateLastTwoWeeksPeriod();
        log.info("Период для загрузки аналитики: {} - {}", period.from(), period.to());

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        List<CompletableFuture<Void>> futures = cabinetsWithKey.stream()
                .map(cabinet -> CompletableFuture.runAsync(() -> {
                    try {
                        processCabinetAnalytics(cabinet, period);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        log.error("Ошибка при загрузке аналитики для кабинета (ID: {}, продавец: {}): {}",
                                cabinet.getId(), cabinet.getUser().getEmail(), e.getMessage());
                        errorCount.incrementAndGet();
                    }
                }, cabinetUpdateExecutor))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("Завершена автоматическая загрузка аналитики по кабинетам (параллельно): успешно {}, ошибок {}",
                successCount.get(), errorCount.get());
    }

    /**
     * Автоматическое обновление списка складов WB по кабинетам с ключом.
     * Запускается каждый день в 00:00.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void updateWbWarehouses() {
        log.info("Запуск автоматического обновления складов WB");

        List<Cabinet> cabinetsWithKey = findCabinetsWithApiKey();
        if (cabinetsWithKey.isEmpty()) {
            log.warn("Не найдено кабинетов с API-ключом для обновления складов WB");
            return;
        }

        List<CompletableFuture<Void>> futures = cabinetsWithKey.stream()
                .map(cabinet -> {
                    long cabinetId = cabinet.getId();
                    String apiKey = cabinet.getApiKey();
                    String userEmail = cabinet.getUser() != null ? cabinet.getUser().getEmail() : null;
                    return CompletableFuture.runAsync(() -> updateWarehousesForCabinet(cabinetId, apiKey, userEmail), cabinetUpdateExecutor);
                })
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private void updateWarehousesForCabinet(long cabinetId, String apiKey, String userEmail) {
        try {
            if (apiKey == null || apiKey.isBlank()) {
                return;
            }
            log.info("Обновление складов WB для кабинета (ID: {}, продавец: {})", cabinetId, userEmail);

            List<WbWarehouseResponse> warehouses = warehousesApiClient.getWbOffices(apiKey);
            warehouseService.saveOrUpdateWarehouses(warehouses);

            log.info("Завершено обновление складов WB для кабинета (ID: {})", cabinetId);
        } catch (Exception e) {
            log.warn("Не удалось обновить склады WB для кабинета {}: {}", cabinetId, e.getMessage());
        }
    }

    /**
     * Кабинеты с заданным API-ключом и активным продавцом (для планировщика).
     * Загружает User (join fetch), чтобы в асинхронных задачах не обращаться к lazy-прокси без сессии.
     */
    private List<Cabinet> findCabinetsWithApiKey() {
        return cabinetRepository.findCabinetsWithApiKeyAndUser(Role.SELLER);
    }

    private DateRange calculateLastTwoWeeksPeriod() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate twoWeeksAgo = yesterday.minusDays(13);

        return new DateRange(twoWeeksAgo, yesterday);
    }

    private void processCabinetAnalytics(Cabinet cabinet, DateRange period) {
        log.info("Загрузка аналитики для кабинета (ID: {}, продавец: {})",
                cabinet.getId(), cabinet.getUser().getEmail());

        analyticsService.updateCardsAndLoadAnalytics(cabinet, period.from(), period.to());
    }

    /**
     * Минимальный интервал между ручными обновлениями данных (6 часов).
     */
    private static final int MIN_UPDATE_INTERVAL_HOURS = 6;

    /**
     * Ручной запуск обновления данных для конкретного продавца.
     * Используется для принудительного обновления без ожидания ночного шедулера.
     * Обновление можно запускать не чаще одного раза в 6 часов.
     *
     * @param seller продавец, для которого нужно обновить данные
     * @throws UserException если с последнего обновления прошло меньше 6 часов
     */
    @Transactional
    public void triggerManualUpdate(User seller) {
        log.info("Ручной запуск обновления данных для продавца (ID: {}, email: {})",
                seller.getId(), seller.getEmail());

        try {
            Cabinet cabinet = wbApiKeyService.findDefaultCabinetByUserId(seller.getId());

            validateUpdateInterval(cabinet);

            cabinet.setLastDataUpdateRequestedAt(LocalDateTime.now());
            cabinetRepository.save(cabinet);

            DateRange period = calculateLastTwoWeeksPeriod();

            analyticsService.updateCardsAndLoadAnalytics(cabinet, period.from(), period.to());

            log.info("Ручное обновление данных для продавца (ID: {}, email: {}) успешно запущено",
                    seller.getId(), seller.getEmail());
        } catch (UserException e) {
            // Пробрасываем UserException без логирования как ошибку (это ожидаемое поведение)
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при ручном обновлении данных для продавца (ID: {}, email: {}): {}",
                    seller.getId(), seller.getEmail(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Ручной запуск обновления данных для конкретного кабинета (по ID кабинета).
     * Используется на Сводной, когда выбран конкретный кабинет — даты и ограничение считаются по этому кабинету.
     * Права доступа к кабинету проверяются в контроллере до вызова.
     *
     * @param cabinetId ID кабинета для обновления
     * @throws UserException если с последнего обновления прошло меньше 6 часов
     */
    @Transactional
    public void triggerManualUpdateByCabinet(Long cabinetId) {
        Cabinet cabinet = cabinetRepository.findByIdWithUser(cabinetId)
                .orElseThrow(() -> new UserException("Кабинет не найден", HttpStatus.NOT_FOUND));
        log.info("Ручной запуск обновления данных для кабинета (ID: {}, продавец: {})",
                cabinet.getId(), cabinet.getUser().getEmail());

        try {
            validateUpdateInterval(cabinet);

            cabinet.setLastDataUpdateRequestedAt(LocalDateTime.now());
            cabinetRepository.save(cabinet);

            DateRange period = calculateLastTwoWeeksPeriod();
            analyticsService.updateCardsAndLoadAnalytics(cabinet, period.from(), period.to());

            log.info("Ручное обновление данных для кабинета (ID: {}) успешно запущено", cabinet.getId());
        } catch (UserException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при ручном обновлении данных для кабинета (ID: {}): {}",
                    cabinet.getId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Проверяет, прошло ли достаточно времени с последнего обновления.
     *
     * @param cabinet кабинет продавца
     * @throws UserException если с последнего обновления прошло меньше 6 часов
     */
    private void validateUpdateInterval(Cabinet cabinet) {
        LocalDateTime lastUpdate = cabinet.getLastDataUpdateAt();
        LocalDateTime lastRequested = cabinet.getLastDataUpdateRequestedAt();
        LocalDateTime lastAction = lastUpdate != null && lastRequested != null
                ? (lastUpdate.isAfter(lastRequested) ? lastUpdate : lastRequested)
                : (lastUpdate != null ? lastUpdate : lastRequested);

        if (lastAction == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        long hoursSinceLastUpdate = java.time.Duration.between(lastAction, now).toHours();

        if (hoursSinceLastUpdate < MIN_UPDATE_INTERVAL_HOURS) {
            long remainingHours = MIN_UPDATE_INTERVAL_HOURS - hoursSinceLastUpdate;
            String message = String.format(
                    "Обновление данных можно запускать не чаще одного раза в %d часов. " +
                            "Следующее обновление будет доступно через %d %s",
                    MIN_UPDATE_INTERVAL_HOURS,
                    remainingHours,
                    getHoursWord(remainingHours)
            );
            throw new UserException(message, HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    /**
     * Возвращает правильное склонение слова "час/часа/часов".
     */
    private String getHoursWord(long hours) {
        if (hours % 10 == 1 && hours % 100 != 11) {
            return "час";
        } else if (hours % 10 >= 2 && hours % 10 <= 4 && (hours % 100 < 10 || hours % 100 >= 20)) {
            return "часа";
        } else {
            return "часов";
        }
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }
}

