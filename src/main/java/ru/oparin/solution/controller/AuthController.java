package ru.oparin.solution.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.oparin.solution.dto.*;
import ru.oparin.solution.service.AuthService;
import ru.oparin.solution.service.PasswordResetService;
import ru.oparin.solution.service.UserService;

/**
 * Контроллер для аутентификации и регистрации пользователей.
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final PasswordResetService passwordResetService;

    /**
     * Регистрация нового продавца.
     *
     * @param request данные для регистрации
     * @return сообщение об успешной регистрации или ошибка
     */
    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Регистрация пользователя с email = '{}'", request.getEmail());
        userService.registerSeller(request);
        
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(createSuccessMessage("Регистрация успешна. Теперь вы можете войти в систему."));
    }

    /**
     * Авторизация пользователя.
     *
     * @param request данные для входа
     * @return JWT токен и информация о пользователе или ошибка
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Авторизация пользователя с email = '{}'", request.getEmail());
        AuthResponse response = authService.login(request);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Запрос на восстановление пароля: отправка ссылки на email.
     *
     * @param request email пользователя
     * @return сообщение (всегда успех, чтобы не раскрывать наличие email в системе)
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        log.info("Запрос сброса пароля для email = '{}'", request.getEmail());
        passwordResetService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(createSuccessMessage(
                "Если аккаунт с таким email существует, на него отправлена ссылка для сброса пароля."));
    }

    /**
     * Сброс пароля по токену из письма.
     *
     * @param request токен и новый пароль
     * @return сообщение об успехе
     */
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        log.info("Сброс пароля по токену");
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(createSuccessMessage("Пароль успешно изменён. Войдите с новым паролем."));
    }

    /**
     * Создает сообщение об успехе.
     */
    private MessageResponse createSuccessMessage(String message) {
        return MessageResponse.builder()
                .message(message)
                .build();
    }
}
