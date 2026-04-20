package ru.oparin.solution.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.oparin.solution.dto.*;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.Plan;
import ru.oparin.solution.model.WbApiEventStatus;
import ru.oparin.solution.model.WbApiEventType;
import ru.oparin.solution.repository.PlanRepository;
import ru.oparin.solution.repository.SubscriptionRepository;
import ru.oparin.solution.scheduler.AnalyticsScheduler;
import ru.oparin.solution.service.AdminSubscriptionService;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.events.WbApiEventService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Эндпоинты только для ADMIN.
 * В том числе ручной запуск обновления карточек и аналитики, управление планами и подписками.
 */
@RestController
@RequestMapping("/admin")
@Slf4j
public class AdminController {

    private final CabinetService cabinetService;
    private final AnalyticsScheduler analyticsScheduler;
    private final Executor taskExecutor;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final AdminSubscriptionService adminSubscriptionService;
    private final WbApiEventService wbApiEventService;

    public AdminController(CabinetService cabinetService,
                           AnalyticsScheduler analyticsScheduler,
                           @Qualifier("taskExecutor") Executor taskExecutor,
                           PlanRepository planRepository,
                           SubscriptionRepository subscriptionRepository,
                           AdminSubscriptionService adminSubscriptionService,
                           WbApiEventService wbApiEventService) {
        this.cabinetService = cabinetService;
        this.analyticsScheduler = analyticsScheduler;
        this.taskExecutor = taskExecutor;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.adminSubscriptionService = adminSubscriptionService;
        this.wbApiEventService = wbApiEventService;
    }

    /**
     * Ручной запуск обновления карточек и загрузки аналитики для кабинета.
     * Создаётся цепочка событий WB API (как при ночном обновлении). Ограничение 6 часов не применяется.
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
        Cabinet cabinet = cabinetService.findByIdWithUserOrThrow(cabinetId);

        LocalDate to = dateTo != null ? dateTo : LocalDate.now().minusDays(1);
        LocalDate from = dateFrom != null ? dateFrom : to.minusDays(13);

        if (from.isAfter(to)) {
            throw new UserException("dateFrom не может быть позже dateTo", HttpStatus.BAD_REQUEST);
        }

        wbApiEventService.enqueueInitialContentEvent(cabinet.getId(), from, to, false, "ADMIN_MANUAL_CABINET");

        return ResponseEntity.accepted()
                .body(Map.of(
                        "message", "Обновление поставлено в очередь событий WB API",
                        "cabinetId", String.valueOf(cabinetId),
                        "dateFrom", from.toString(),
                        "dateTo", to.toString()
                ));
    }

    /**
     * Запуск полного обновления по всем кабинетам (как ночной шедулер): карточки, цены, остатки, кампании, аналитика, акции календаря.
     * Выполняется в фоне; эндпоинт сразу возвращает 202 Accepted.
     * Для админов и менеджеров действует ограничение: не чаще одного раза в 5 минут.
     */
    @PostMapping("/run-analytics-all")
    public ResponseEntity<?> runAnalyticsAll() {
        if (!analyticsScheduler.canRunAdminTriggeredUpdate()) {
            long remaining = analyticsScheduler.getAdminTriggerCooldownRemainingSeconds();
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(
                            "message", "Обновление кабинетов можно запускать не чаще одного раза в 5 минут. Повторите попытку позже.",
                            "lastTriggeredAtMs", analyticsScheduler.getLastAdminTriggeredAtMs(),
                            "nextAvailableInSeconds", remaining
                    ));
        }
        analyticsScheduler.recordAdminTriggered();
        log.info("Ручной запуск полного обновления кабинетов через /admin/run-analytics-all");
        taskExecutor.execute(analyticsScheduler::runNightlyFullAnalyticsUpdate);
        return ResponseEntity.accepted()
                .body(Map.of("message", "Полное обновление по всем кабинетам запущено в фоне (как по расписанию)."));
    }

    /**
     * Статус кулдауна ручного запуска «обновить кабинеты» для админов и менеджеров (не чаще 1 раза в 5 минут).
     */
    @GetMapping("/trigger-cooldown")
    public ResponseEntity<Map<String, Object>> getTriggerCooldown() {
        long lastMs = analyticsScheduler.getLastAdminTriggeredAtMs();
        long remainingSeconds = analyticsScheduler.getAdminTriggerCooldownRemainingSeconds();
        return ResponseEntity.ok(Map.of(
                "lastTriggeredAtMs", lastMs,
                "canTrigger", remainingSeconds == 0,
                "nextAvailableInSeconds", remainingSeconds
        ));
    }

    @GetMapping("/wb-events")
    public ResponseEntity<PageResponse<WbApiEventDto>> getWbEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) WbApiEventStatus status,
            @RequestParam(required = false) WbApiEventType eventType,
            @RequestParam(required = false) Long cabinetId
    ) {
        return ResponseEntity.ok(wbApiEventService.getEventsPage(page, size, status, eventType, cabinetId));
    }

    @GetMapping("/wb-events/{eventId}")
    public ResponseEntity<WbApiEventDto> getWbEvent(@PathVariable Long eventId) {
        return ResponseEntity.ok(wbApiEventService.getEventById(eventId));
    }

    @GetMapping("/wb-events/stats")
    public ResponseEntity<WbApiEventStatsDto> getWbEventsStats() {
        return ResponseEntity.ok(wbApiEventService.getStats());
    }

    @GetMapping("/wb-events/stats-by-type")
    public ResponseEntity<WbApiEventTypeStatsDto> getWbEventsStatsByType(
            @RequestParam(required = false) WbApiEventStatus status
    ) {
        return ResponseEntity.ok(wbApiEventService.getStatsByType(status));
    }

    @PostMapping("/wb-events/{eventId}/retry")
    public ResponseEntity<Map<String, String>> retryWbEvent(@PathVariable Long eventId) {
        wbApiEventService.retryNow(eventId);
        return ResponseEntity.ok(Map.of("message", "Событие отправлено на повторное выполнение"));
    }

    @PostMapping("/wb-events/retry-failed-final")
    public ResponseEntity<Map<String, String>> retryAllFailedFinalWbEvents() {
        int updated = wbApiEventService.retryAllFailedFinalNow();
        return ResponseEntity.ok(Map.of(
                "message", "События отправлены на повторное выполнение",
                "updatedCount", String.valueOf(updated)
        ));
    }

    @PostMapping("/wb-events/{eventId}/cancel")
    public ResponseEntity<Map<String, String>> cancelWbEvent(@PathVariable Long eventId) {
        wbApiEventService.cancel(eventId);
        return ResponseEntity.ok(Map.of("message", "Событие отменено"));
    }

    // ————— Управление планами —————

    @GetMapping("/plans")
    public ResponseEntity<List<PlanDto>> getAllPlans() {
        List<PlanDto> list = planRepository.findAllByOrderBySortOrderAsc().stream()
                .map(this::toPlanDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @PostMapping("/plans")
    public ResponseEntity<PlanDto> createPlan(@Valid @RequestBody CreatePlanRequest request) {
        Plan plan = Plan.builder()
                .name(request.getName())
                .description(request.getDescription())
                .priceRub(request.getPriceRub())
                .periodDays(request.getPeriodDays())
                .maxCabinets(request.getMaxCabinets())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();
        plan = planRepository.save(plan);
        return ResponseEntity.status(HttpStatus.CREATED).body(toPlanDto(plan));
    }

    @PutMapping("/plans/{id}")
    public ResponseEntity<PlanDto> updatePlan(@PathVariable Long id, @Valid @RequestBody UpdatePlanRequest request) {
        Plan plan = planRepository.findById(id)
                .orElseThrow(() -> new UserException("План не найден", HttpStatus.NOT_FOUND));
        if (request.getName() != null) plan.setName(request.getName());
        if (request.getDescription() != null) plan.setDescription(request.getDescription());
        if (request.getPriceRub() != null) plan.setPriceRub(request.getPriceRub());
        if (request.getPeriodDays() != null) plan.setPeriodDays(request.getPeriodDays());
        if (request.getMaxCabinets() != null) plan.setMaxCabinets(request.getMaxCabinets());
        if (request.getSortOrder() != null) plan.setSortOrder(request.getSortOrder());
        if (request.getIsActive() != null) plan.setIsActive(request.getIsActive());
        plan = planRepository.save(plan);
        return ResponseEntity.ok(toPlanDto(plan));
    }

    private PlanDto toPlanDto(Plan p) {
        return PlanDto.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .priceRub(p.getPriceRub())
                .periodDays(p.getPeriodDays())
                .maxCabinets(p.getMaxCabinets())
                .sortOrder(p.getSortOrder())
                .isActive(p.getIsActive())
                .build();
    }

    // ————— Подписки и платежи по пользователю —————

    @GetMapping("/users/{userId}/subscriptions")
    public ResponseEntity<List<SubscriptionDto>> getUserSubscriptions(@PathVariable Long userId) {
        return ResponseEntity.ok(adminSubscriptionService.getSubscriptionsByUserId(userId));
    }

    @GetMapping("/users/{userId}/payments")
    public ResponseEntity<List<PaymentDto>> getUserPayments(@PathVariable Long userId) {
        return ResponseEntity.ok(adminSubscriptionService.getPaymentsByUserId(userId));
    }

    /** Ручное назначение/продление подписки пользователю. */
    @PostMapping("/subscription/extend")
    public ResponseEntity<SubscriptionDto> extendSubscription(@Valid @RequestBody ExtendSubscriptionRequest request) {
        SubscriptionDto dto = adminSubscriptionService.extendSubscription(
                request.getUserId(),
                request.getPlanId(),
                request.getExpiresAt()
        );
        return ResponseEntity.ok(dto);
    }
}
