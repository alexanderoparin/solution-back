package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
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
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserService userService;

    /**
     * Авторизация пользователя и выдача JWT токена.
     *
     * @param request данные для входа
     * @return ответ с JWT токеном и информацией о пользователе
     */
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = userService.findByEmail(request.getEmail());
        String token = tokenProvider.generateToken(
                user.getEmail(),
                user.getId(),
                user.getRole().name()
        );

        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .role(user.getRole().name())
                .userId(user.getId())
                .build();
    }
}

