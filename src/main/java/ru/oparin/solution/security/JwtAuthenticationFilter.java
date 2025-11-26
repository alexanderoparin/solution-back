package ru.oparin.solution.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Фильтр для аутентификации по JWT токену.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtTokenProvider tokenProvider;

    /**
     * Обработка запроса и извлечение JWT токена из заголовка.
     *
     * @param request HTTP запрос
     * @param response HTTP ответ
     * @param filterChain цепочка фильтров
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = extractJwtFromRequest(request);
            
            if (isValidJwtToken(jwt)) {
                setAuthenticationInContext(request, jwt);
            }
        } catch (Exception ex) {
            logger.error("Не удалось установить аутентификацию пользователя в контексте безопасности", ex);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Извлекает JWT токен из заголовка Authorization.
     */
    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    /**
     * Проверяет валидность JWT токена.
     */
    private boolean isValidJwtToken(String jwt) {
        if (!StringUtils.hasText(jwt)) {
            return false;
        }
        String email = tokenProvider.getEmailFromToken(jwt);
        return tokenProvider.validateToken(jwt, email);
    }

    /**
     * Устанавливает аутентификацию в контексте Spring Security.
     */
    private void setAuthenticationInContext(HttpServletRequest request, String jwt) {
        String email = tokenProvider.getEmailFromToken(jwt);
        String role = tokenProvider.getRoleFromToken(jwt);
        
        UsernamePasswordAuthenticationToken authentication = createAuthenticationToken(email, role, request);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * Создает токен аутентификации для Spring Security.
     */
    private UsernamePasswordAuthenticationToken createAuthenticationToken(
            String email, 
            String role, 
            HttpServletRequest request
    ) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                email,
                null,
                Collections.singletonList(new SimpleGrantedAuthority(ROLE_PREFIX + role))
        );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        return authentication;
    }
}
