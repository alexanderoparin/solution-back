package ru.oparin.solution.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.oparin.solution.dto.AuthResponse;
import ru.oparin.solution.dto.LoginRequest;
import ru.oparin.solution.dto.MessageResponse;
import ru.oparin.solution.dto.RegisterRequest;
import ru.oparin.solution.service.AuthService;
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
        MessageResponse response = MessageResponse.builder()
                .message("Регистрация успешна. Теперь вы можете войти в систему.")
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
}

