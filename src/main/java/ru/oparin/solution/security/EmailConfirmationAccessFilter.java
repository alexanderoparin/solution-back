package ru.oparin.solution.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.User;
import ru.oparin.solution.service.UserService;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Блокирует доступ к API до подтверждения почты.
 * Разрешены только запросы к профилю, смене пароля, подтверждению почты по ссылке и отправке письма подтверждения
 * (и связанные пути users/cabinets).
 */
@Component
public class EmailConfirmationAccessFilter extends OncePerRequestFilter {

    private static final List<String> ALLOWED_PATHS = List.of(
            "/api/user/access",
            "/api/user/profile",
            "/api/user/password",
            "/api/user/deletion-request",
            "/api/user/send-email-confirmation",
            "/api/feedback",
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/auth/confirm-email",
            "/api/cabinets",
            "/api/users",
            "/api/public"
    );

    private final UserService userService;

    public EmailConfirmationAccessFilter(@Lazy UserService userService) {
        this.userService = userService;
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Фильтр только для реальной JWT-сессии. AnonymousAuthenticationToken.isAuthenticated() == true —
     * его нельзя считать входом, иначе публичные URL получают 401 «Пользователь не найден».
     */
    private static boolean isRealUserAuthentication(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            return false;
        }
        if (auth instanceof AnonymousAuthenticationToken) {
            return false;
        }
        Object principal = auth.getPrincipal();
        return !(principal instanceof String stringPrincipal && "anonymousUser".equals(stringPrincipal));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isRealUserAuthentication(SecurityContextHolder.getContext().getAuthentication());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!isRealUserAuthentication(auth)) {
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
        } catch (UserException e) {
            if (e.getHttpStatus() == HttpStatus.NOT_FOUND) {
                sendUnauthorizedResponse(response, "Пользователь не найден.");
                return;
            }
            filterChain.doFilter(request, response);
            return;
        } catch (Exception e) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            sendUnauthorizedResponse(response, "Аккаунт деактивирован.");
            return;
        }

        if (Boolean.TRUE.equals(user.getEmailConfirmed())) {
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

    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                Map.of("message", message, "error", message)
        ));
    }
}
