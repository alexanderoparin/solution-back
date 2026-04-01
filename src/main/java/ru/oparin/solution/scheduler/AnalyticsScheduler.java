package ru.oparin.solution.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import ru.oparin.solution.dto.wb.WbWarehouseResponse;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.exception.WbApiUnauthorizedScopeException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.User;
import ru.oparin.solution.service.*;
import ru.oparin.solution.service.wb.WbApiCategory;
import ru.oparin.solution.service.wb.WbWarehousesApiClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Планировщик задач для автоматической загрузки аналитики.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AnalyticsScheduler {

    private final WbApiKeyService wbApiKeyService;
    private final CabinetService cabinetService;
    private final FullUpdateOrchestrator fullUpdateOrchestrator;
    private final ProductCardAnalyticsService analyticsService;
    private final WbWarehousesApiClient warehousesApiClient;
    private final WbWarehouseService warehouseService;
    private final CabinetScopeStatusService cabinetScopeStatusService;
    @Qualifier("cabinetUpdateExecutor")
    private final Executor cabinetUpdateExecutor;

    /**
     * Автоматическая загрузка аналитики для всех кабинетов с привязанным API-ключом (активные продавцы).
     * Запускается каждый день по расписанию. Для каждого кабинета загружаются карточки, кампании и аналитика.
     * Период: последние 14 дней (без текущих суток). После кабинетов — синхронизация акций календаря.
     * Публичный метод также вызывается из AdminController для ручного «обновить всё».
     */
    @Scheduled(cron = "0 15 0 * * ?")
    public void loadAnalyticsForAllActiveSellers() {
        runFullAnalyticsUpdate();
    }

    /**
     * Полное обновление по всем кабинетам (как ночной шедулер).
     * Вызывается по расписанию и из админ-эндпоинта POST /admin/run-analytics-all.
     * Вся логика — в оркестраторе: для каждого кабинета свой сервис в своей транзакции.
     */
    public void runFullAnalyticsUpdate() {
        fullUpdateOrchestrator.runFullUpdate();
    }

    /**
     * Асинхронный запуск полного обновления по всем кабинетам.
     */
    @Async("taskExecutor")
    public void runFullAnalyticsUpdateAsync() {
        runFullAnalyticsUpdateAsync(false);
    }

    @Async("taskExecutor")
    public void runFullAnalyticsUpdateAsync(boolean includeStocks) {
        fullUpdateOrchestrator.runFullUpdate(includeStocks);
    }

    /**
     * Асинхронный запуск полного обновления по кабинетам селлеров конкретного менеджера.
     */
    @Async("taskExecutor")
    public void runFullAnalyticsUpdateForManagerAsync(Long managerId) {
        runFullAnalyticsUpdateForManagerAsync(managerId, false);
    }

    @Async("taskExecutor")
    public void runFullAnalyticsUpdateForManagerAsync(Long managerId, boolean includeStocks) {
        fullUpdateOrchestrator.runFullUpdateForManager(managerId, includeStocks);
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
            cabinetScopeStatusService.recordSuccess(cabinetId, WbApiCategory.MARKETPLACE);

            log.info("Завершено обновление складов WB для кабинета (ID: {})", cabinetId);
        } catch (WbApiUnauthorizedScopeException e) {
            cabinetScopeStatusService.recordFailure(cabinetId, e.getCategory(), e.getMessage());
            log.warn("Не удалось обновить склады с кабинета {}, нет доступа к категории WB API: {}", cabinetId, e.getCategory().getDisplayName());
        } catch (HttpClientErrorException ex) {
            log.warn("Не удалось обновить склады с кабинета {}, получили код ошибки {}", cabinetId, ex.getStatusCode());
        } catch (Exception e) {
            log.warn("Не удалось обновить склады WB для кабинета {}: {}", cabinetId, e.getMessage());
        }
    }

    /**
     * Кабинеты с заданным API-ключом и активным продавцом (для планировщика).
     * Загружает User (join fetch), чтобы в асинхронных задачах не обращаться к lazy-прокси без сессии.
     */
    private List<Cabinet> findCabinetsWithApiKey() {
        return cabinetService.findCabinetsWithApiKeyAndUser(Role.SELLER);
    }

    private DateRange calculateLastTwoWeeksPeriod() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate twoWeeksAgo = yesterday.minusDays(13);

        return new DateRange(twoWeeksAgo, yesterday);
    }

    /**
     * Минимальный интервал между ручными обновлениями данных (6 часов).
     */
    private static final int MIN_UPDATE_INTERVAL_HOURS = 6;

    /**
     * Интервал кулдауна для админов и менеджеров: не чаще одного ручного запуска «обновить кабинеты» в 5 минут.
     * Применяется точечно:
     * - для продавца — к его кабинетам (выбор селлера в профиле админа/менеджера);
     * - для конкретного кабинета — только к нему (обновление со Сводной/карточки).
     */
    private static final int ADMIN_MIN_UPDATE_INTERVAL_MINUTES = 5;

    /**
     * Глобальный кулдаун только для эндпоинта «обновить всё» (POST /admin/run-analytics-all
     * и POST /users/trigger-all-cabinets-update): не чаще одного запуска в 5 минут.
     */
    private static final long FULL_UPDATE_COOLDOWN_MS = 5 * 60 * 1000L;
    private volatile long lastFullUpdateTriggeredAtMs = 0;

    /**
     * Проверяет, можно ли выполнить ручной запуск «обновить всё» (run-analytics-all). Только для AdminController.
     */
    public boolean canRunAdminTriggeredUpdate() {
        if (lastFullUpdateTriggeredAtMs == 0) return true;
        return System.currentTimeMillis() - lastFullUpdateTriggeredAtMs >= FULL_UPDATE_COOLDOWN_MS;
    }

    /**
     * Фиксирует момент ручного запуска «обновить всё» (для кулдауна 5 минут).
     */
    public void recordAdminTriggered() {
        lastFullUpdateTriggeredAtMs = System.currentTimeMillis();
    }

    /**
     * Время последнего запуска «обновить всё» (epoch ms), или 0. Для GET /admin/trigger-cooldown.
     */
    public long getLastAdminTriggeredAtMs() {
        return lastFullUpdateTriggeredAtMs;
    }

    /**
     * Секунд до следующего доступного запуска «обновить всё» (0 если уже можно).
     */
    public long getAdminTriggerCooldownRemainingSeconds() {
        if (lastFullUpdateTriggeredAtMs == 0) return 0;
        long elapsed = System.currentTimeMillis() - lastFullUpdateTriggeredAtMs;
        if (elapsed >= FULL_UPDATE_COOLDOWN_MS) return 0;
        return (FULL_UPDATE_COOLDOWN_MS - elapsed) / 1000;
    }

    /**
     * Ручной запуск обновления данных для конкретного продавца.
     * Запускает обновление по всем кабинетам продавца, у которых задан API-ключ.
     * Обновление по каждому кабинету можно запускать не чаще одного раза в 6 часов (для админа и менеджера ограничение не действует).
     *
     * @param seller продавец, для которого нужно обновить данные
     * @param skipIntervalCheck если true, проверка 6 часов не выполняется (для ADMIN и MANAGER)
     * @throws UserException если нет ни одного кабинета с API-ключом или все кабинеты не прошли проверку интервала
     */
    @Transactional
    public void triggerManualUpdate(User seller, boolean skipIntervalCheck) {
        triggerManualUpdate(seller, skipIntervalCheck, false);
    }

    @Transactional
    public void triggerManualUpdate(User seller, boolean skipIntervalCheck, boolean includeStocks) {
        log.info("Ручной запуск обновления данных для продавца (ID: {}, email: {})",
                seller.getId(), seller.getEmail());

        List<Cabinet> cabinets = cabinetService.findCabinetsByUserId(seller.getId()).stream()
                .filter(c -> c.getApiKey() != null && !c.getApiKey().isBlank())
                .toList();

        if (cabinets.isEmpty()) {
            throw new UserException("Нет кабинетов с API-ключом для обновления данных.", HttpStatus.BAD_REQUEST);
        }

        if (skipIntervalCheck) {
            validateAdminUpdateIntervalForSeller(cabinets);
        }

        DateRange period = calculateLastTwoWeeksPeriod();
        int started = 0;

        try {
            for (Cabinet cabinet : cabinets) {
                if (tryRunManualCabinetUpdate(cabinet, period, skipIntervalCheck, includeStocks)) started++;
            }

            if (started == 0) {
                throw new UserException(
                        "Обновление по всем кабинетам возможно не ранее чем через 6 часов. Следующее обновление будет доступно позже.",
                        HttpStatus.TOO_MANY_REQUESTS
                );
            }

            log.info("Ручное обновление данных для продавца (ID: {}, email: {}) запущено для {} кабинетов из {}",
                    seller.getId(), seller.getEmail(), started, cabinets.size());
        } catch (UserException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при ручном обновлении данных для продавца (ID: {}, email: {}): {}",
                    seller.getId(), seller.getEmail(), e.getMessage(), e);
            throw e;
        }
    }

    private boolean tryRunManualCabinetUpdate(Cabinet cabinet,
                                              DateRange period,
                                              boolean skipIntervalCheck,
                                              boolean includeStocks) {
        try {
            if (!skipIntervalCheck) {
                validateUpdateInterval(cabinet);
            }
            cabinet.setLastDataUpdateRequestedAt(LocalDateTime.now());
            cabinetService.save(cabinet);
            analyticsService.updateCardsAndLoadAnalytics(cabinet, period.from(), period.to());
            if (includeStocks) {
                tryRunStocksUpdate(cabinet.getId());
            }
            return true;
        } catch (UserException e) {
            log.warn("Кабинет (ID: {}) пропущен: {}", cabinet.getId(), e.getMessage());
            return false;
        }
    }

    private void tryRunStocksUpdate(Long cabinetId) {
        try {
            analyticsService.validateStocksUpdateInterval(cabinetId);
            analyticsService.recordStocksUpdateTriggered(cabinetId);
            analyticsService.runStocksUpdateOnly(cabinetId);
        } catch (UserException e) {
            log.warn("Обновление остатков для кабинета (ID: {}) пропущено: {}", cabinetId, e.getMessage());
        }
    }

    /**
     * Ручной запуск обновления данных для продавца (с проверкой интервала 6 часов).
     * Вызов из UserController (селлер обновляет себя).
     */
    public void triggerManualUpdate(User seller) {
        triggerManualUpdate(seller, false, false);
    }

    /**
     * Ручной запуск обновления данных для конкретного кабинета (по ID кабинета).
     * Используется на Сводной, когда выбран конкретный кабинет — даты и ограничение считаются по этому кабинету.
     * Права доступа к кабинету проверяются в контроллере до вызова.
     * Для админа и менеджера ограничение 6 часов не действует.
     *
     * @param cabinetId ID кабинета для обновления
     * @param skipIntervalCheck если true, проверка 6 часов не выполняется (для ADMIN и MANAGER)
     * @throws UserException если с последнего обновления прошло меньше 6 часов
     */
    @Transactional
    public void triggerManualUpdateByCabinet(Long cabinetId, boolean skipIntervalCheck) {
        Cabinet cabinet = cabinetService.findByIdWithUserOrThrow(cabinetId);
        log.info("Ручной запуск обновления данных для кабинета (ID: {}, продавец: {})",
                cabinet.getId(), cabinet.getUser().getEmail());

        try {
            if (!skipIntervalCheck) {
                validateUpdateInterval(cabinet);
            } else {
                validateAdminUpdateIntervalForCabinet(cabinet);
            }

            cabinet.setLastDataUpdateRequestedAt(LocalDateTime.now());
            cabinetService.save(cabinet);

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
     * Проверяет, что для селлера (его кабинетов) прошло не менее ADMIN_MIN_UPDATE_INTERVAL_MINUTES минут
     * с момента последнего ручного запуска админом/менеджером.
     */
    private void validateAdminUpdateIntervalForSeller(List<Cabinet> cabinets) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastAction = null;

        for (Cabinet cabinet : cabinets) {
            LocalDateTime lastRequested = cabinet.getLastDataUpdateRequestedAt();
            if (lastRequested != null && (lastAction == null || lastRequested.isAfter(lastAction))) {
                lastAction = lastRequested;
            }
        }

        if (lastAction == null) {
            return;
        }

        long minutesSinceLast = java.time.Duration.between(lastAction, now).toMinutes();
        if (minutesSinceLast < ADMIN_MIN_UPDATE_INTERVAL_MINUTES) {
            throw new UserException(
                    "Обновление кабинетов для этого продавца можно запускать не чаще одного раза в 5 минут. " +
                            "Повторите попытку позже.",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }
    }

    /**
     * Проверяет, что для конкретного кабинета прошло не менее ADMIN_MIN_UPDATE_INTERVAL_MINUTES минут
     * с момента последнего ручного запуска админом/менеджером.
     */
    private void validateAdminUpdateIntervalForCabinet(Cabinet cabinet) {
        LocalDateTime lastRequested = cabinet.getLastDataUpdateRequestedAt();
        if (lastRequested == null) {
            return;
        }
        long minutesSinceLast = java.time.Duration.between(lastRequested, LocalDateTime.now()).toMinutes();
        if (minutesSinceLast < ADMIN_MIN_UPDATE_INTERVAL_MINUTES) {
            throw new UserException(
                    "Обновление кабинета можно запускать не чаще одного раза в 5 минут. Повторите попытку позже.",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }
    }

    /**
     * Ручной запуск обновления данных для кабинета (с проверкой интервала 6 часов).
     */
    public void triggerManualUpdateByCabinet(Long cabinetId) {
        triggerManualUpdateByCabinet(cabinetId, false);
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

