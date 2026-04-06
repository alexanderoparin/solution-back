package ru.oparin.solution.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.CabinetUpdateErrorScope;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.User;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.CabinetUpdateErrorService;
import ru.oparin.solution.service.FullUpdateOrchestrator;
import ru.oparin.solution.service.events.WbApiEventService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Планировщик задач для автоматической загрузки аналитики.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AnalyticsScheduler {

    private final CabinetService cabinetService;
    private final FullUpdateOrchestrator fullUpdateOrchestrator;
    private final CabinetUpdateErrorService cabinetUpdateErrorService;
    private final WbApiEventService wbApiEventService;

    /**
     * Автоматическая ночная загрузка аналитики:
     * main-обновление всех кабинетов, затем единый этап обновления остатков.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void loadAnalyticsForAllActiveSellers() {
        runNightlyFullAnalyticsUpdate();
    }

    /**
     * Ночной полный прогон: main по всем кабинетам, затем единый этап остатков.
     */
    public void runNightlyFullAnalyticsUpdate() {
        fullUpdateOrchestrator.runFullUpdate(true);
    }


    @Async("taskExecutor")
    public void runFullAnalyticsUpdateAsync(boolean includeStocks) {
        fullUpdateOrchestrator.runFullUpdate(includeStocks);
    }

    @Async("taskExecutor")
    public void runFullAnalyticsUpdateForManagerAsync(Long managerId, boolean includeStocks) {
        fullUpdateOrchestrator.runFullUpdateForManager(managerId, includeStocks);
    }

    /**
     * Автоматическое обновление списка складов WB по кабинетам с ключом.
     * Запускается каждый день в 00:00.
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void updateWbWarehouses() {
        log.info("Запуск автоматического обновления складов WB (очередь событий)");

        List<Cabinet> cabinetsWithKey = findCabinetsWithApiKey();
        if (cabinetsWithKey.isEmpty()) {
            log.warn("Не найдено кабинетов с API-ключом для обновления складов WB");
            return;
        }

        for (Cabinet cabinet : cabinetsWithKey) {
            wbApiEventService.enqueueWarehousesSyncCabinetEvent(cabinet.getId(), "SCHEDULED_WAREHOUSES");
        }
        log.info("В очередь поставлено {} событий WAREHOUSES_SYNC_CABINET", cabinetsWithKey.size());
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
            wbApiEventService.enqueueInitialContentEvent(
                    cabinet.getId(),
                    period.from(),
                    period.to(),
                    includeStocks,
                    "MANUAL_SELLER"
            );
            return true;
        } catch (UserException e) {
            log.warn("Кабинет (ID: {}) пропущен: {}", cabinet.getId(), e.getMessage());
            cabinetUpdateErrorService.recordError(cabinet.getId(), CabinetUpdateErrorScope.MAIN, e.getMessage());
            return false;
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
            wbApiEventService.enqueueInitialContentEvent(
                    cabinet.getId(),
                    period.from(),
                    period.to(),
                    false,
                    "MANUAL_CABINET"
            );

            log.info("Ручное обновление данных для кабинета (ID: {}) поставлено в очередь событий", cabinet.getId());
        } catch (UserException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при ручном обновлении данных для кабинета (ID: {}): {}",
                    cabinet.getId(), e.getMessage(), e);
            cabinetUpdateErrorService.recordError(cabinet.getId(), CabinetUpdateErrorScope.MAIN, e.getMessage());
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

