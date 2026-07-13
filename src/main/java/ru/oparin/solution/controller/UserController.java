package ru.oparin.solution.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.oparin.solution.config.SubscriptionProperties;
import ru.oparin.solution.dto.*;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.Payment;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.PaymentRepository;
import ru.oparin.solution.repository.UserRepository;
import ru.oparin.solution.scheduler.AnalyticsScheduler;
import ru.oparin.solution.service.*;
import ru.oparin.solution.service.campaign.CampaignManageAccessService;

import java.util.List;
import java.util.stream.Collectors;

import static java.lang.Boolean.TRUE;

/**
 * Контроллер для работы с профилем пользователя.
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;
    private final WbApiKeyService wbApiKeyService;
    private final AnalyticsScheduler analyticsScheduler;
    private final SubscriptionAccessService subscriptionAccessService;
    private final CampaignManageAccessService campaignManageAccessService;
    private final SubscriptionProperties subscriptionProperties;
    private final EmailConfirmationService emailConfirmationService;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final AccountTypeService accountTypeService;
    private final ProfileSubscriptionService profileSubscriptionService;
    private final AccountDeletionRequestService accountDeletionRequestService;
    private final CabinetAccessService cabinetAccessService;

    @PutMapping("/api-key")
    public ResponseEntity<MessageResponse> updateApiKey(
            @Valid @RequestBody UpdateApiKeyRequest request,
            Authentication authentication
    ) {
        User user = getCurrentUser(authentication);
        validateCabinetOwner(user);
        userService.updateApiKey(user.getId(), request.getWbApiKey());
        return ResponseEntity.ok(createSuccessMessage(
                "API ключ успешно обновлен. Валидация будет выполнена при первом использовании."));
    }

    @PostMapping("/api-key/validate")
    public ResponseEntity<MessageResponse> validateApiKey(Authentication authentication) {
        User user = getCurrentUser(authentication);
        validateCabinetOwner(user);
        wbApiKeyService.validateApiKey(user.getId());
        Cabinet cabinet = wbApiKeyService.findDefaultCabinetByUserId(user.getId());
        if (TRUE.equals(cabinet.getIsValid())) {
            return ResponseEntity.ok(createSuccessMessage("API ключ валиден"));
        }
        String errorMsg = cabinet.getValidationError() != null
                ? cabinet.getValidationError()
                : "API ключ не прошёл проверку";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(createSuccessMessage(errorMsg));
    }

    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> getProfile(Authentication authentication) {
        User user = getCurrentUser(authentication);
        return ResponseEntity.ok(buildUserProfile(user));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication
    ) {
        User user = getCurrentUser(authentication);
        user = userService.updateProfile(user, request);
        return ResponseEntity.ok(buildUserProfile(user));
    }

    @PostMapping("/send-email-confirmation")
    public ResponseEntity<MessageResponse> sendEmailConfirmation(Authentication authentication) {
        User user = getCurrentUser(authentication);
        emailConfirmationService.sendConfirmationEmail(user);
        return ResponseEntity.ok(createSuccessMessage("Письмо для подтверждения отправлено на вашу почту."));
    }

    @PostMapping("/deletion-request")
    public ResponseEntity<MessageResponse> createDeletionRequest(
            @Valid @RequestBody AccountDeletionRequestDto request,
            Authentication authentication
    ) {
        User user = getCurrentUser(authentication);
        accountDeletionRequestService.createRequest(user, request);
        return ResponseEntity.ok(createSuccessMessage(
                "Запрос отправлен. Мы получили запрос на удаление профиля. "
                        + "После обработки заявки вы получите уведомление на электронную почту."));
    }

    @PutMapping("/password")
    public ResponseEntity<MessageResponse> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication
    ) {
        User user = getCurrentUser(authentication);
        userService.changePassword(user.getId(), request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(createSuccessMessage("Пароль успешно изменен"));
    }

    @PostMapping("/update-data")
    public ResponseEntity<MessageResponse> triggerDataUpdate(Authentication authentication) {
        User user = getCurrentUser(authentication);
        validateCabinetOwner(user);
        log.info("Ручной запуск обновления данных: userId={}, email={}", user.getId(), user.getEmail());
        analyticsScheduler.triggerManualUpdate(user);
        return ResponseEntity.ok(createSuccessMessage(
                "Обновление данных запущено. Процесс выполняется в фоновом режиме."));
    }

    @GetMapping("/access")
    public ResponseEntity<AccessStatusResponse> getAccessStatus(
            @RequestParam(required = false) Long sellerId,
            Authentication authentication
    ) {
        User user = getCurrentUser(authentication);
        boolean hasAccess = subscriptionAccessService.hasAccess(user);
        var activeSubscription = subscriptionAccessService.getActiveSubscription(user);
        User subscriptionSeller = resolveSellerForAccess(user, sellerId);
        CampaignManageAccessDto campaignManage = campaignManageAccessService.buildAccessState(user, subscriptionSeller);

        AccessStatusResponse response = AccessStatusResponse.builder()
                .hasAccess(hasAccess)
                .emailConfirmed(Boolean.TRUE.equals(user.getEmailConfirmed()))
                .billingEnabled(subscriptionProperties.isBillingEnabled())
                .subscriptionStatus(activeSubscription != null ? activeSubscription.getStatus() : null)
                .subscriptionExpiresAt(activeSubscription != null ? activeSubscription.getExpiresAt() : null)
                .campaignManage(campaignManage)
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/payments")
    public ResponseEntity<List<PaymentDto>> getMyPayments(Authentication authentication) {
        User user = getCurrentUser(authentication);
        List<PaymentDto> list = paymentRepository.findByUser_IdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toPaymentDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    private User resolveSellerForAccess(User actor, Long sellerId) {
        if (actor.getRole() == Role.ADMIN && sellerId != null) {
            return userRepository.findById(sellerId).orElse(null);
        }
        if (sellerId != null) {
            return userRepository.findById(sellerId).orElse(null);
        }
        return actor;
    }

    private PaymentDto toPaymentDto(Payment p) {
        return PaymentDto.builder()
                .id(p.getId())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .description(p.getDescription())
                .planName(p.getPlanName())
                .status(p.getStatus())
                .paidAt(p.getPaidAt())
                .createdAt(p.getCreatedAt())
                .build();
    }

    private User getCurrentUser(Authentication authentication) {
        return userService.findByEmail(authentication.getName());
    }

    private void validateCabinetOwner(User user) {
        if (user.getRole() == Role.ADMIN) {
            return;
        }
        if (cabinetAccessService.getOverview(user, null).owned().isEmpty()) {
            throw new UserException("Добавьте кабинет для выполнения операции", HttpStatus.FORBIDDEN);
        }
    }

    private MessageResponse createSuccessMessage(String message) {
        return MessageResponse.builder().message(message).build();
    }

    private UserProfileResponse buildUserProfile(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .accountTypes(accountTypeService.getAccountTypes(user.getId()))
                .isActive(user.getIsActive())
                .emailConfirmed(user.getEmailConfirmed())
                .lastEmailConfirmationSentAt(user.getLastEmailConfirmationSentAt())
                .createdAt(user.getCreatedAt())
                .subscription(profileSubscriptionService.buildSummary(user))
                .deletionRequest(accountDeletionRequestService.getStatus(user.getId()))
                .build();
    }
}
