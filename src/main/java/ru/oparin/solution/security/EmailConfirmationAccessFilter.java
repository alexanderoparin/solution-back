package ru.oparin.solution.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.oparin.solution.model.User;
import ru.oparin.solution.service.UserService;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Блокирует доступ к API до подтверждения почты для всех ролей, кроме клиентов агентства ({@code isAgencyClient}).
 * Разрешены только запросы к профилю, смене пароля и отправке письма подтверждения (и связанные пути users/cabinets).
 */
@Component
public class EmailConfirmationAccessFilter extends OncePerRequestFilter {

    private static final List<String> ALLOWED_PATHS = List.of(
            "/api/user/access",
            "/api/user/profile",
            "/api/user/password",
            "/api/user/send-email-confirmation",
            "/api/cabinets",
            "/api/users"
    );

    private final UserService userService;

    public EmailConfirmationAccessFilter(@Lazy UserService userService) {
        this.userService = userService;
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (ALLOWED_PATHS.stream().anyMatch(path::startsWith)) {
            filterChain.doFilter(request, response);
            return;
        }

        User user;
        try {
            user = userService.findByEmail(auth.getName());
        } catch (Exception e) {
            filterChain.doFilter(request, response);
            return;
        }

        if (Boolean.TRUE.equals(user.getIsAgencyClient()) || Boolean.TRUE.equals(user.getEmailConfirmed())) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                Map.of("message", "Подтвердите почту для доступа к сервису. Откройте профиль и запросите письмо со ссылкой.")
        ));
    }
}
