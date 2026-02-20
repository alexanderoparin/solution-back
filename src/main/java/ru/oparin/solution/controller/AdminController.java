package ru.oparin.solution.controller;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.repository.CabinetRepository;
import ru.oparin.solution.scheduler.AnalyticsScheduler;
import ru.oparin.solution.service.ProductCardAnalyticsService;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Эндпоинты только для ADMIN.
 * В том числе ручной запуск обновления карточек и аналитики (задача выполняется через taskExecutor).
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final CabinetRepository cabinetRepository;
    private final ProductCardAnalyticsService productCardAnalyticsService;
    private final AnalyticsScheduler analyticsScheduler;
    private final Executor taskExecutor;

    public AdminController(CabinetRepository cabinetRepository,
                           ProductCardAnalyticsService productCardAnalyticsService,
                           AnalyticsScheduler analyticsScheduler,
                           @Qualifier("taskExecutor") Executor taskExecutor) {
        this.cabinetRepository = cabinetRepository;
        this.productCardAnalyticsService = productCardAnalyticsService;
        this.analyticsScheduler = analyticsScheduler;
        this.taskExecutor = taskExecutor;
    }

    /**
     * Ручной запуск обновления карточек и загрузки аналитики для кабинета.
     * Задача выполняется асинхронно (taskExecutor). Ограничение 6 часов не применяется.
     *
     * @param cabinetId ID кабинета (обязательно)
     * @param dateFrom  дата начала периода (необязательно, по умолчанию 14 дней назад)
     * @param dateTo    дата окончания периода (необязательно, по умолчанию вчера)
     */
    @PostMapping("/run-analytics")
    public ResponseEntity<Map<String, String>> runAnalytics(
            @RequestParam Long cabinetId,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo
    ) {
        Cabinet cabinet = cabinetRepository.findByIdWithUser(cabinetId)
                .orElseThrow(() -> new UserException("Кабинет не найден", HttpStatus.NOT_FOUND));

        LocalDate to = dateTo != null ? dateTo : LocalDate.now().minusDays(1);
        LocalDate from = dateFrom != null ? dateFrom : to.minusDays(13);

        if (from.isAfter(to)) {
            throw new UserException("dateFrom не может быть позже dateTo", HttpStatus.BAD_REQUEST);
        }

        productCardAnalyticsService.updateCardsAndLoadAnalytics(cabinet, from, to);

        return ResponseEntity.accepted()
                .body(Map.of(
                        "message", "Обновление карточек и загрузка аналитики запущено",
                        "cabinetId", String.valueOf(cabinetId),
                        "dateFrom", from.toString(),
                        "dateTo", to.toString()
                ));
    }

    /**
     * Запуск полного обновления по всем кабинетам (как ночной шедулер): карточки, цены, остатки, кампании, аналитика, акции календаря.
     * Выполняется в фоне; эндпоинт сразу возвращает 202 Accepted.
     */
    @PostMapping("/run-analytics-all")
    public ResponseEntity<Map<String, String>> runAnalyticsAll() {
        taskExecutor.execute(analyticsScheduler::runFullAnalyticsUpdate);
        return ResponseEntity.accepted()
                .body(Map.of("message", "Полное обновление по всем кабинетам запущено в фоне (как по расписанию)."));
    }
}
