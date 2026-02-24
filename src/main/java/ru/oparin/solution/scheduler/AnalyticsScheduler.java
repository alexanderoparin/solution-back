package ru.oparin.solution.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import ru.oparin.solution.dto.wb.WbWarehouseResponse;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.CabinetRepository;
import ru.oparin.solution.service.FullUpdateOrchestrator;
import ru.oparin.solution.service.ProductCardAnalyticsService;
import ru.oparin.solution.service.WbApiKeyService;
import ru.oparin.solution.service.WbWarehouseService;
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
public class AnalyticsScheduler {

    private final WbApiKeyService wbApiKeyService;
    private final CabinetRepository cabinetRepository;
    private final FullUpdateOrchestrator fullUpdateOrchestrator;
    private final ProductCardAnalyticsService analyticsService;
    private final WbWarehousesApiClient warehousesApiClient;
    private final WbWarehouseService warehouseService;
    private final Executor cabinetUpdateExecutor;

    public AnalyticsScheduler(WbApiKeyService wbApiKeyService,
                              CabinetRepository cabinetRepository,
                              FullUpdateOrchestrator fullUpdateOrchestrator,
                              ProductCardAnalyticsService analyticsService,
                              WbWarehousesApiClient warehousesApiClient,
                              WbWarehouseService warehouseService,
                              @Qualifier("cabinetUpdateExecutor") Executor cabinetUpdateExecutor) {
        this.wbApiKeyService = wbApiKeyService;
        this.cabinetRepository = cabinetRepository;
        this.fullUpdateOrchestrator = fullUpdateOrchestrator;
        this.analyticsService = analyticsService;
        this.warehousesApiClient = warehousesApiClient;
        this.warehouseService = warehouseService;
        this.cabinetUpdateExecutor = cabinetUpdateExecutor;
    }

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
        return cabinetRepository.findCabinetsWithApiKeyAndUser(Role.SELLER);
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
     * Ручной запуск обновления данных для конкретного продавца.
     * Используется для принудительного обновления без ожидания ночного шедулера.
     * Обновление можно запускать не чаще одного раза в 6 часов (для админа и менеджера ограничение не действует).
     *
     * @param seller продавец, для которого нужно обновить данные
     * @param skipIntervalCheck если true, проверка 6 часов не выполняется (для ADMIN и MANAGER)
     * @throws UserException если с последнего обновления прошло меньше 6 часов
     */
    @Transactional
    public void triggerManualUpdate(User seller, boolean skipIntervalCheck) {
        log.info("Ручной запуск обновления данных для продавца (ID: {}, email: {})",
                seller.getId(), seller.getEmail());

        try {
            Cabinet cabinet = wbApiKeyService.findDefaultCabinetByUserId(seller.getId());

            if (!skipIntervalCheck) {
                validateUpdateInterval(cabinet);
            }

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
     * Ручной запуск обновления данных для продавца (с проверкой интервала 6 часов).
     * Вызов из UserController (селлер обновляет себя).
     */
    public void triggerManualUpdate(User seller) {
        triggerManualUpdate(seller, false);
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
        Cabinet cabinet = cabinetRepository.findByIdWithUser(cabinetId)
                .orElseThrow(() -> new UserException("Кабинет не найден", HttpStatus.NOT_FOUND));
        log.info("Ручной запуск обновления данных для кабинета (ID: {}, продавец: {})",
                cabinet.getId(), cabinet.getUser().getEmail());

        try {
            if (!skipIntervalCheck) {
                validateUpdateInterval(cabinet);
            }

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

