package ru.oparin.solution.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.oparin.solution.dto.ChangePasswordRequest;
import ru.oparin.solution.dto.MessageResponse;
import ru.oparin.solution.dto.UpdateApiKeyRequest;
import ru.oparin.solution.dto.UserProfileResponse;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.User;
import ru.oparin.solution.model.WbApiKey;
import ru.oparin.solution.service.UserService;
import ru.oparin.solution.service.WbApiKeyService;

/**
 * Контроллер для работы с профилем пользователя.
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final WbApiKeyService wbApiKeyService;

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
                .isActive(user.getIsActive());

        if (user.getRole() == Role.SELLER) {
            builder.apiKey(buildApiKeyInfo(user.getId()));
        }

        return builder.build();
    }

    /**
     * Строит информацию об API ключе для профиля.
     */
    private UserProfileResponse.ApiKeyInfo buildApiKeyInfo(Long userId) {
        WbApiKey apiKey = wbApiKeyService.findByUserId(userId);
        return UserProfileResponse.ApiKeyInfo.builder()
                .isValid(apiKey.getIsValid())
                .lastValidatedAt(apiKey.getLastValidatedAt())
                .validationError(apiKey.getValidationError())
                .build();
    }
}
