package ru.oparin.solution.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.oparin.solution.dto.*;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.Payment;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.PaymentRepository;
import ru.oparin.solution.scheduler.AnalyticsScheduler;
import ru.oparin.solution.service.EmailConfirmationService;
import ru.oparin.solution.service.SubscriptionAccessService;
import ru.oparin.solution.service.UserService;
import ru.oparin.solution.service.WbApiKeyService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Контроллер для работы с профилем пользователя.
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final WbApiKeyService wbApiKeyService;
    private final AnalyticsScheduler analyticsScheduler;
    private final SubscriptionAccessService subscriptionAccessService;
    private final EmailConfirmationService emailConfirmationService;
    private final PaymentRepository paymentRepository;

    /**
     * Обновление WB API ключа пользователя.
     *
     * @param request новый API ключ
     * @param authentication данные аутентификации
     * @return сообщение об успешном обновлении или ошибка
     */
    @PutMapping("/api-key")
    public ResponseEntity<MessageResponse> updateApiKey(
            @Valid @RequestBody UpdateApiKeyRequest request,
            Authentication authentication
    ) {
        User user = getCurrentUser(authentication);
        validateSellerRole(user);

        userService.updateApiKey(user.getId(), request.getWbApiKey());

        return ResponseEntity.ok(createSuccessMessage("API ключ успешно обновлен. Валидация будет выполнена при первом использовании."));
    }

    /**
     * Проверка WB API ключа пользователя.
     *
     * @param authentication данные аутентификации
     * @return сообщение о результате проверки или ошибка
     */
    @PostMapping("/api-key/validate")
    public ResponseEntity<MessageResponse> validateApiKey(Authentication authentication) {
        User user = getCurrentUser(authentication);
        validateSellerRole(user);

        wbApiKeyService.validateApiKey(user.getId());

        Cabinet cabinet = wbApiKeyService.findDefaultCabinetByUserId(user.getId());
        if (Boolean.TRUE.equals(cabinet.getIsValid())) {
            return ResponseEntity.ok(createSuccessMessage("API ключ валиден"));
        } else {
            String errorMsg = cabinet.getValidationError() != null
                    ? "API ключ невалиден: " + cabinet.getValidationError()
                    : "API ключ невалиден";
            return ResponseEntity.ok(createSuccessMessage(errorMsg));
        }
    }

    /**
     * Получение профиля текущего пользователя.
     *
     * @param authentication данные аутентификации
     * @return профиль пользователя или ошибка
     */
    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> getProfile(Authentication authentication) {
        User user = getCurrentUser(authentication);
        UserProfileResponse profile = buildUserProfile(user);

        return ResponseEntity.ok(profile);
    }

    /**
     * Отправка письма для подтверждения email (только для сторонних селлеров, не чаще 1 раза в 24 ч).
     *
     * @param authentication данные аутентификации
     * @return сообщение об успехе или ошибка (429 если письмо уже отправлялось недавно)
     */
    @PostMapping("/send-email-confirmation")
    public ResponseEntity<MessageResponse> sendEmailConfirmation(Authentication authentication) {
        User user = getCurrentUser(authentication);
        emailConfirmationService.sendConfirmationEmail(user);
        return ResponseEntity.ok(createSuccessMessage("Письмо для подтверждения отправлено на вашу почту."));
    }

    /**
     * Смена пароля пользователя.
     *
     * @param request данные для смены пароля
     * @param authentication данные аутентификации
     * @return сообщение об успешной смене пароля или ошибка
     */
    @PutMapping("/password")
    public ResponseEntity<MessageResponse> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication
    ) {
        User user = getCurrentUser(authentication);
        userService.changePassword(user.getId(), request.getCurrentPassword(), request.getNewPassword());

        return ResponseEntity.ok(createSuccessMessage("Пароль успешно изменен"));
    }

    /**
     * Принудительный запуск обновления данных для текущего продавца.
     * Запускает процесс обновления карточек, кампаний и аналитики без ожидания ночного шедулера.
     *
     * @param authentication данные аутентификации
     * @return сообщение об успешном запуске обновления
     */
    @PostMapping("/update-data")
    public ResponseEntity<MessageResponse> triggerDataUpdate(Authentication authentication) {
        User user = getCurrentUser(authentication);
        validateSellerRole(user);

        // Запускаем обновление асинхронно
        analyticsScheduler.triggerManualUpdate(user);

        return ResponseEntity.ok(createSuccessMessage(
                "Обновление данных запущено. Процесс выполняется в фоновом режиме. " +
                "Данные будут доступны через несколько минут."
        ));
    }

    /**
     * Статус доступа текущего пользователя.
     */
    @GetMapping("/access")
    public ResponseEntity<AccessStatusResponse> getAccessStatus(Authentication authentication) {
        User user = getCurrentUser(authentication);
        boolean hasAccess = subscriptionAccessService.hasAccess(user);
        boolean agencyClient = user.getOwner() != null;

        var activeSubscription = subscriptionAccessService.getActiveSubscription(user);

        AccessStatusResponse response = AccessStatusResponse.builder()
                .hasAccess(hasAccess)
                .agencyClient(agencyClient)
                .subscriptionStatus(activeSubscription != null ? activeSubscription.getStatus() : null)
                .subscriptionExpiresAt(activeSubscription != null ? activeSubscription.getExpiresAt() : null)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Список платежей текущего пользователя (для ЛК).
     */
    @GetMapping("/payments")
    public ResponseEntity<List<PaymentDto>> getMyPayments(Authentication authentication) {
        User user = getCurrentUser(authentication);
        List<PaymentDto> list = paymentRepository.findByUser_IdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toPaymentDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    private PaymentDto toPaymentDto(Payment p) {
        return PaymentDto.builder()
                .id(p.getId())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .status(p.getStatus())
                .paidAt(p.getPaidAt())
                .createdAt(p.getCreatedAt())
                .build();
    }

    /**
     * Получает текущего пользователя из аутентификации.
     */
    private User getCurrentUser(Authentication authentication) {
        return userService.findByEmail(authentication.getName());
    }

    /**
     * Проверяет, что пользователь имеет роль SELLER.
     */
    private void validateSellerRole(User user) {
        if (user.getRole() != Role.SELLER) {
            throw new UserException("Только SELLER может обновлять API ключ");
        }
    }

    /**
     * Создает сообщение об успехе.
     */
    private MessageResponse createSuccessMessage(String message) {
        return MessageResponse.builder()
                .message(message)
                .build();
    }

    /**
     * Строит профиль пользователя.
     */
    private UserProfileResponse buildUserProfile(User user) {
        UserProfileResponse.UserProfileResponseBuilder builder = UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .isActive(user.getIsActive())
                .emailConfirmed(user.getEmailConfirmed())
                .isAgencyClient(user.getIsAgencyClient());

        if (user.getRole() == Role.SELLER) {
            builder.apiKey(buildApiKeyInfo(user.getId()));
        }

        return builder.build();
    }

    /**
     * Строит информацию об API ключе для профиля.
     * Возвращает null, если API ключ не найден.
     */
    private UserProfileResponse.ApiKeyInfo buildApiKeyInfo(Long userId) {
        return wbApiKeyService.findDefaultCabinetByUserIdOptional(userId)
                .map(cabinet -> UserProfileResponse.ApiKeyInfo.builder()
                        .apiKey(cabinet.getApiKey())
                        .isValid(cabinet.getIsValid())
                        .lastValidatedAt(cabinet.getLastValidatedAt())
                        .validationError(cabinet.getValidationError())
                        .lastDataUpdateAt(cabinet.getLastDataUpdateAt())
                        .build())
                .orElse(null);
    }
}
