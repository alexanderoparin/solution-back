package ru.oparin.solution.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.oparin.solution.dto.*;
import ru.oparin.solution.dto.cabinet.CabinetDto;
import ru.oparin.solution.dto.cabinet.UpdateCabinetRequest;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.User;
import ru.oparin.solution.scheduler.AnalyticsScheduler;
import ru.oparin.solution.service.*;
import ru.oparin.solution.service.sync.FeedbacksSyncService;

import java.util.List;
import java.util.Map;

/**
 * Контроллер для управления пользователями.
 * ADMIN управляет MANAGER'ами, MANAGER управляет SELLER'ами, SELLER управляет WORKER'ами.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Slf4j
public class UsersManagementController {

    private final UserService userService;
    private final CabinetService cabinetService;
    private final WbApiKeyService wbApiKeyService;
    private final AnalyticsScheduler analyticsScheduler;
    private final ProductCardAnalyticsService productCardAnalyticsService;
    private final PromotionCalendarService promotionCalendarService;
    private final FeedbacksSyncService feedbacksSyncService;

    /**
     * Постраничное получение списка пользователей, которыми может управлять текущий пользователь.
     *
     * @param page номер страницы (0-based)
     * @param size размер страницы
     * @param authentication данные аутентификации
     * @return страница пользователей
     */
    @GetMapping
    public ResponseEntity<PageResponse<UserListItemDto>> getManagedUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        User currentUser = getCurrentUser(authentication);
        Pageable pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.ASC, "createdAt"));
        var userPage = userService.getManagedUsersPageDto(currentUser, pageable);
        PageResponse<UserListItemDto> response = PageResponse.<UserListItemDto>builder()
                .content(userPage.getContent())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .size(userPage.getSize())
                .number(userPage.getNumber())
                .build();
        return ResponseEntity.ok(response);
    }

    /**
     * Получение списка активных селлеров для аналитики.
     * Для ADMIN возвращает всех активных селлеров.
     * Для MANAGER возвращает только своих активных селлеров.
     *
     * @param authentication данные аутентификации
     * @return список активных селлеров
     */
    @GetMapping("/active-sellers")
    public ResponseEntity<List<UserListItemDto>> getActiveSellers(Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        List<UserListItemDto> sellers = userService.getActiveSellers(currentUser);
        return ResponseEntity.ok(sellers);
    }

    /**
     * Запуск полного обновления по всем активным кабинетам (как ночной шедулер).
     * Доступно для ADMIN и MANAGER. Кулдаун: не чаще одного раза в 5 минут.
     */
    @PostMapping("/trigger-all-cabinets-update")
    public ResponseEntity<?> triggerAllCabinetsUpdate(Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser.getRole() != Role.ADMIN && currentUser.getRole() != Role.MANAGER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(MessageResponse.builder().message("Недостаточно прав").build());
        }

        if (!analyticsScheduler.canRunAdminTriggeredUpdate()) {
            long remaining = analyticsScheduler.getAdminTriggerCooldownRemainingSeconds();
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(
                            "message", "Обновление всех кабинетов можно запускать не чаще одного раза в 5 минут. Повторите попытку позже.",
                            "lastTriggeredAtMs", analyticsScheduler.getLastAdminTriggeredAtMs(),
                            "nextAvailableInSeconds", remaining
                    ));
        }

        analyticsScheduler.recordAdminTriggered();
        if (currentUser.getRole() == Role.MANAGER) {
            analyticsScheduler.runFullAnalyticsUpdateForManagerAsync(currentUser.getId());
        } else {
            analyticsScheduler.runFullAnalyticsUpdateAsync();
        }
        return ResponseEntity.accepted()
                .body(MessageResponse.builder()
                        .message(currentUser.getRole() == Role.MANAGER
                                ? "Полное обновление кабинетов ваших селлеров запущено в фоне."
                                : "Полное обновление всех активных кабинетов запущено в фоне.")
                        .build());
    }

    /**
     * Создание нового пользователя.
     *
     * @param request данные для создания пользователя
     * @param authentication данные аутентификации
     * @return созданный пользователь
     */
    @PostMapping
    public ResponseEntity<UserListItemDto> createUser(
            @Valid @RequestBody CreateUserRequest request,
            Authentication authentication
    ) {
        User currentUser = getCurrentUser(authentication);
        User createdUser = userService.createUser(request, currentUser);
        UserListItemDto dto = mapToDto(createdUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Обновление пользователя.
     *
     * @param userId ID пользователя
     * @param request данные для обновления
     * @param authentication данные аутентификации
     * @return обновленный пользователь
     */
    @PutMapping("/{userId}")
    public ResponseEntity<UserListItemDto> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRequest request,
            Authentication authentication
    ) {
        User currentUser = getCurrentUser(authentication);
        User updatedUser = userService.updateUser(userId, request, currentUser);
        UserListItemDto dto = mapToDto(updatedUser);
        return ResponseEntity.ok(dto);
    }

    /**
     * Переключение активности пользователя.
     *
     * @param userId ID пользователя
     * @param authentication данные аутентификации
     * @return сообщение об успешном обновлении
     */
    @PutMapping("/{userId}/toggle-active")
    public ResponseEntity<MessageResponse> toggleUserActive(
            @PathVariable Long userId,
            Authentication authentication
    ) {
        User currentUser = getCurrentUser(authentication);
        userService.toggleUserActive(userId, currentUser);
        return ResponseEntity.ok(MessageResponse.builder()
                .message("Статус активности пользователя изменен")
                .build());
    }

    /**
     * Полное удаление пользователя и всех связанных записей из БД.
     * Доступно только для ADMIN. Нельзя удалить себя или другого админа.
     *
     * @param userId ID пользователя для удаления
     * @param authentication данные аутентификации
     * @return сообщение об успешном удалении
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<MessageResponse> deleteUser(
            @PathVariable Long userId,
            Authentication authentication
    ) {
        User currentUser = getCurrentUser(authentication);
        log.info("[Удаление пользователя] Запрос: удалить userId={}, инициатор: {} ({}), роль: {}", userId, currentUser.getEmail(), currentUser.getId(), currentUser.getRole());
        userService.deleteUser(userId, currentUser);
        return ResponseEntity.ok(MessageResponse.builder()
                .message("Удаление запущено. Выполняется в фоновом режиме.")
                .build());
    }

    /**
     * Список кабинетов селлера. Доступно только для ADMIN и MANAGER (для своего селлера).
     */
    @GetMapping("/{sellerId}/cabinets")
    public ResponseEntity<List<CabinetDto>> getSellerCabinets(
            @PathVariable Long sellerId,
            Authentication authentication
    ) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser.getRole() != Role.ADMIN && currentUser.getRole() != Role.MANAGER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        User seller = userService.findById(sellerId);
        if (seller.getRole() != Role.SELLER) {
            return ResponseEntity.badRequest().build();
        }
        if (currentUser.getRole() == Role.MANAGER &&
                (seller.getOwner() == null || !seller.getOwner().getId().equals(currentUser.getId()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<CabinetDto> cabinets = cabinetService.listByUserId(seller.getId());
        return ResponseEntity.ok(cabinets);
    }

    /**
     * Принудительный запуск обновления данных для указанного селлера.
     * Доступно только для ADMIN и MANAGER.
     *
     * @param sellerId ID селлера
     * @param authentication данные аутентификации
     * @return сообщение об успешном запуске обновления
     */
    @PostMapping("/{sellerId}/trigger-update")
    public ResponseEntity<MessageResponse> triggerSellerDataUpdate(
            @PathVariable Long sellerId,
            Authentication authentication
    ) {
        User currentUser = getCurrentUser(authentication);
        
        // Проверяем права доступа
        if (currentUser.getRole() != Role.ADMIN && currentUser.getRole() != Role.MANAGER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(MessageResponse.builder()
                            .message("Недостаточно прав для выполнения операции")
                            .build());
        }
        
        // Получаем селлера
        User seller = userService.findById(sellerId);
        
        // Проверяем, что это селлер
        if (seller.getRole() != Role.SELLER) {
            return ResponseEntity.badRequest()
                    .body(MessageResponse.builder()
                            .message("Указанный пользователь не является селлером")
                            .build());
        }
        
        // Для менеджера проверяем, что селлер принадлежит ему
        if (currentUser.getRole() == Role.MANAGER && 
            (seller.getOwner() == null || !seller.getOwner().getId().equals(currentUser.getId()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(MessageResponse.builder()
                            .message("У вас нет доступа к данному селлеру")
                            .build());
        }
        
        boolean skipIntervalCheck = currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.MANAGER;
        analyticsScheduler.triggerManualUpdate(seller, skipIntervalCheck);

        return ResponseEntity.ok(MessageResponse.builder()
                .message("Обновление данных запущено. Процесс выполняется в фоновом режиме. " +
                        "Данные будут доступны через несколько минут.")
                .build());
    }

    /**
     * Принудительный запуск обновления данных для указанного кабинета.
     * Ограничение 6 ч и даты считаются по этому кабинету.
     * Доступно для ADMIN и MANAGER (кабинет должен принадлежать селлеру, к которому есть доступ).
     */
    @PostMapping("/cabinets/{cabinetId}/trigger-update")
    public ResponseEntity<MessageResponse> triggerCabinetDataUpdate(
            @PathVariable Long cabinetId,
            Authentication authentication
    ) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser.getRole() != Role.ADMIN && currentUser.getRole() != Role.MANAGER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(MessageResponse.builder().message("Недостаточно прав").build());
        }
        cabinetService.validateCabinetAccessForUpdate(cabinetId, currentUser);
        boolean skipIntervalCheck = currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.MANAGER;
        analyticsScheduler.triggerManualUpdateByCabinet(cabinetId, skipIntervalCheck);
        return ResponseEntity.ok(MessageResponse.builder()
                .message("Обновление данных запущено. Процесс выполняется в фоновом режиме. " +
                        "Данные будут доступны через несколько минут.")
                .build());
    }

    /**
     * Запуск валидации API ключа кабинета (для админа/менеджера при просмотре кабинетов селлера).
     * Те же права доступа, что и для trigger-update.
     */
    @PostMapping("/cabinets/{cabinetId}/validate-api-key")
    public ResponseEntity<MessageResponse> validateCabinetApiKey(
            @PathVariable Long cabinetId,
            Authentication authentication
    ) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser.getRole() != Role.ADMIN && currentUser.getRole() != Role.MANAGER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(MessageResponse.builder().message("Недостаточно прав").build());
        }
        cabinetService.validateCabinetAccessForUpdate(cabinetId, currentUser);
        Cabinet cabinet = cabinetService.findByIdWithUserOrThrow(cabinetId);
        wbApiKeyService.validateApiKeyByCabinet(cabinet);
        String message = Boolean.TRUE.equals(cabinet.getIsValid())
                ? "API ключ валиден"
                : (cabinet.getValidationError() != null ? "API ключ невалиден: " + cabinet.getValidationError() : "API ключ невалиден");
        return ResponseEntity.ok(MessageResponse.builder().message(message).build());
    }

    /**
     * Обновление API ключа (и/или имени) кабинета селлера (для админа/менеджера). Те же права доступа, что и для validate-api-key.
     */
    @PatchMapping("/cabinets/{cabinetId}")
    public ResponseEntity<CabinetDto> updateSellerCabinet(
            @PathVariable Long cabinetId,
            @Valid @RequestBody UpdateCabinetRequest request,
            Authentication authentication
    ) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser.getRole() != Role.ADMIN && currentUser.getRole() != Role.MANAGER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        cabinetService.validateCabinetAccessForUpdate(cabinetId, currentUser);
        if (request.getApiKey() != null) {
            CabinetDto updated = cabinetService.updateApiKey(cabinetId, request.getApiKey());
            return ResponseEntity.ok(updated);
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            Cabinet cabinet = cabinetService.findByIdWithUserOrThrow(cabinetId);
            cabinet.setName(request.getName().trim());
            cabinetService.save(cabinet);
            CabinetDto updated = cabinetService.getByIdAndUserId(cabinetId, cabinet.getUser().getId());
            return ResponseEntity.ok(updated);
        }
        return ResponseEntity.badRequest().build();
    }

    /**
     * Запуск только обновления остатков по кабинету.
     * Доступно владельцу кабинета (SELLER), ADMIN и MANAGER (с доступом к кабинету).
     * Ограничение: не чаще одного раза в час.
     */
    @PostMapping("/cabinets/{cabinetId}/trigger-stocks-update")
    public ResponseEntity<MessageResponse> triggerCabinetStocksUpdate(
            @PathVariable Long cabinetId,
            Authentication authentication
    ) {
        User currentUser = getCurrentUser(authentication);
        cabinetService.validateCabinetAccessForStocksUpdate(cabinetId, currentUser);
        productCardAnalyticsService.validateStocksUpdateInterval(cabinetId);
        productCardAnalyticsService.recordStocksUpdateTriggered(cabinetId);
        productCardAnalyticsService.runStocksUpdateOnly(cabinetId);
        return ResponseEntity.ok(MessageResponse.builder()
                .message("Обновление остатков запущено. Данные обновятся в фоне в течение нескольких минут.")
                .build());
    }

    /**
     * Принудительный запуск обновления данных по акциям календаря WB для всех кабинетов.
     * GET, тело не требуется. Доступно только для ADMIN.
     *
     * @param authentication данные аутентификации
     * @return сообщение о запуске
     */
    @GetMapping("/trigger-promotions-update")
    public ResponseEntity<MessageResponse> triggerPromotionsUpdate(Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(MessageResponse.builder()
                            .message("Доступно только для администратора")
                            .build());
        }
        promotionCalendarService.syncPromotionsForAllCabinets();
        return ResponseEntity.ok(MessageResponse.builder()
                .message("Обновление данных по акциям календаря запущено и выполнено.")
                .build());
    }

    /**
     * Принудительный запуск синхронизации рейтинга и отзывов по товарам для всех кабинетов.
     * GET, тело не требуется. Доступно только для ADMIN.
     *
     * @param authentication данные аутентификации
     * @return сообщение о запуске
     */
    @GetMapping("/trigger-feedbacks-update")
    public ResponseEntity<MessageResponse> triggerFeedbacksUpdate(Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(MessageResponse.builder()
                            .message("Доступно только для администратора")
                            .build());
        }
        feedbacksSyncService.syncFeedbacksForAllCabinets();
        return ResponseEntity.ok(MessageResponse.builder()
                .message("Обновление рейтинга и отзывов по кабинетам запущено и выполнено.")
                .build());
    }

    /**
     * Получает текущего пользователя из аутентификации.
     */
    private User getCurrentUser(Authentication authentication) {
        return userService.findByEmail(authentication.getName());
    }

    /**
     * Преобразует User в UserListItemDto.
     */
    private UserListItemDto mapToDto(User user) {
        return UserListItemDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .isActive(user.getIsActive())
                .isTemporaryPassword(user.getIsTemporaryPassword())
                .createdAt(user.getCreatedAt())
                .ownerEmail(user.getOwner() != null ? user.getOwner().getEmail() : null)
                .build();
    }
}

