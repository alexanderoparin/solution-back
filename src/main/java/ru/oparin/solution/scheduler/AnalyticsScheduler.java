package ru.oparin.solution.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.User;
import ru.oparin.solution.model.WbApiKey;
import ru.oparin.solution.repository.UserRepository;
import ru.oparin.solution.service.ProductCardAnalyticsService;
import ru.oparin.solution.service.WbApiKeyService;
import ru.oparin.solution.dto.wb.WbWarehouseResponse;
import ru.oparin.solution.service.WbWarehouseService;
import ru.oparin.solution.service.wb.WbWarehousesApiClient;

import java.time.LocalDate;
import java.util.List;

/**
 * Планировщик задач для автоматической загрузки аналитики.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsScheduler {

    private final UserRepository userRepository;
    private final WbApiKeyService wbApiKeyService;
    private final ProductCardAnalyticsService analyticsService;
    private final WbWarehousesApiClient warehousesApiClient;
    private final WbWarehouseService warehouseService;

    /**
     * Автоматическая загрузка аналитики для всех активных продавцов.
     * Запускается каждый день в 3:00 ночи.
     * Период: последняя неделя (без текущих суток).
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void loadAnalyticsForAllActiveSellers() {
        log.info("Запуск автоматической загрузки аналитики для всех активных продавцов");

        List<User> activeSellers = findActiveSellers();
        log.info("Найдено активных продавцов: {}", activeSellers.size());

        if (activeSellers.isEmpty()) {
            log.info("Активных продавцов не найдено, загрузка аналитики пропущена");
            return;
        }

        DateRange period = calculateLastWeekPeriod();
        log.info("Период для загрузки аналитики: {} - {}", period.from(), period.to());

        int successCount = 0;
        int errorCount = 0;

        for (User seller : activeSellers) {
            try {
                processSellerAnalytics(seller, period);
                successCount++;
            } catch (Exception e) {
                log.error("Ошибка при загрузке аналитики для продавца (ID: {}, email: {}): {}", 
                        seller.getId(), seller.getEmail(), e.getMessage());
                errorCount++;
            }
        }

        log.info("Завершена автоматическая загрузка аналитики: успешно {}, ошибок {}", 
                successCount, errorCount);
    }

    /**
     * Автоматическое обновление списка складов WB.
     * Запускается каждый день в 4:00 утра.
     */
    @Scheduled(cron = "0 0 4 * * ?")
    public void updateWbWarehouses() {
        log.info("Запуск автоматического обновления складов WB");

        try {
            // Берем API ключ первого активного продавца для запроса
            List<User> activeSellers = findActiveSellers();
            if (activeSellers.isEmpty()) {
                log.warn("Не найдено активных продавцов для обновления складов WB");
                return;
            }

            User firstSeller = activeSellers.get(0);
            WbApiKey apiKey = wbApiKeyService.findByUserId(firstSeller.getId());

            log.info("Обновление складов WB с использованием API ключа продавца (ID: {}, email: {})",
                    firstSeller.getId(), firstSeller.getEmail());

            List<WbWarehouseResponse> warehouses = warehousesApiClient.getWbOffices(apiKey.getApiKey());
            warehouseService.saveOrUpdateWarehouses(warehouses);

            log.info("Завершено автоматическое обновление складов WB");

        } catch (Exception e) {
            log.error("Ошибка при автоматическом обновлении складов WB: {}", e.getMessage(), e);
        }
    }

    private List<User> findActiveSellers() {
        return userRepository.findByRoleAndIsActive(Role.SELLER, true);
    }

    private DateRange calculateLastWeekPeriod() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate weekAgo = yesterday.minusDays(6);

        return new DateRange(weekAgo, yesterday);
    }

    private void processSellerAnalytics(User seller, DateRange period) {
        WbApiKey apiKey = wbApiKeyService.findByUserId(seller.getId());

        log.info("Загрузка аналитики для продавца (ID: {}, email: {})", 
                seller.getId(), seller.getEmail());

        analyticsService.updateCardsAndLoadAnalytics(
                seller,
                apiKey.getApiKey(),
                period.from(),
                period.to()
        );
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }
}

