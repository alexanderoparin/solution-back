package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import ru.oparin.solution.dto.AuthResponse;
import ru.oparin.solution.dto.LoginRequest;
import ru.oparin.solution.model.User;
import ru.oparin.solution.security.JwtTokenProvider;

/**
 * Сервис аутентификации пользователей.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserService userService;

    /**
     * Авторизация пользователя и выдача JWT токена.
     *
     * @param request данные для входа
     * @return ответ с JWT токеном и информацией о пользователе
     * @throws BadCredentialsException если учетные данные неверны
     */
    public AuthResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticateUser(request);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            User user = userService.findByEmail(request.getEmail());
            String token = generateTokenForUser(user);
            
            log.info("Аутентификация успешна: email={}", request.getEmail());
            return buildAuthResponse(user, token);
            
        } catch (BadCredentialsException e) {
            log.error("Ошибка аутентификации: email={}", request.getEmail());
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при аутентификации пользователя {}: {}", request.getEmail(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Аутентифицирует пользователя по email и паролю.
     */
    private Authentication authenticateUser(LoginRequest request) {
        return authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
    }

    /**
     * Генерирует JWT токен для пользователя.
     */
    private String generateTokenForUser(User user) {
        return tokenProvider.generateToken(
                user.getEmail(),
                user.getId(),
                user.getRole().name()
        );
    }

    /**
     * Создает ответ с данными аутентификации.
     */
    private AuthResponse buildAuthResponse(User user, String token) {
        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .role(user.getRole().name())
                .userId(user.getId())
                .isTemporaryPassword(user.getIsTemporaryPassword())
                .build();
    }
}
