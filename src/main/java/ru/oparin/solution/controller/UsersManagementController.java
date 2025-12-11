package ru.oparin.solution.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.oparin.solution.dto.CreateUserRequest;
import ru.oparin.solution.dto.MessageResponse;
import ru.oparin.solution.dto.UpdateUserRequest;
import ru.oparin.solution.dto.UserListItemDto;
import ru.oparin.solution.model.User;
import ru.oparin.solution.service.UserService;

import java.util.List;

/**
 * Контроллер для управления пользователями.
 * ADMIN управляет MANAGER'ами, MANAGER управляет SELLER'ами, SELLER управляет WORKER'ами.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UsersManagementController {

    private final UserService userService;

    /**
     * Получение списка пользователей, которыми может управлять текущий пользователь.
     *
     * @param authentication данные аутентификации
     * @return список пользователей
     */
    @GetMapping
    public ResponseEntity<List<UserListItemDto>> getManagedUsers(Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        List<UserListItemDto> users = userService.getManagedUsers(currentUser);
        return ResponseEntity.ok(users);
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

