package ru.oparin.solution.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
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
        User user = userService.findByEmail(authentication.getName());
        
        if (user.getRole() != Role.SELLER) {
            throw new UserException("Только SELLER может обновлять API ключ");
        }

        userService.updateApiKey(user.getId(), request.getWbApiKey());
        
        MessageResponse response = MessageResponse.builder()
                .message("API ключ успешно обновлен. Валидация будет выполнена при первом использовании.")
                .build();
        return ResponseEntity.ok(response);
    }

    /**
     * Получение профиля текущего пользователя.
     *
     * @param authentication данные аутентификации
     * @return профиль пользователя или ошибка
     */
    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> getProfile(Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        
        UserProfileResponse.UserProfileResponseBuilder profileBuilder = UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .isActive(user.getIsActive());
        
        if (user.getRole() == Role.SELLER) {
            WbApiKey apiKey = wbApiKeyService.findByUserId(user.getId());
            UserProfileResponse.ApiKeyInfo apiKeyInfo = UserProfileResponse.ApiKeyInfo.builder()
                    .isValid(apiKey.getIsValid())
                    .lastValidatedAt(apiKey.getLastValidatedAt())
                    .validationError(apiKey.getValidationError())
                    .build();
            profileBuilder.apiKey(apiKeyInfo);
        }
        
        return ResponseEntity.ok(profileBuilder.build());
    }
}

