package ru.oparin.solution.security;

import io.jsonwebtoken.ExpiredJwtException;
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
    private static final String HEALTH_ENDPOINT = "/health";

    private final JwtTokenProvider tokenProvider;

    /**
     * Определяет, должен ли фильтр обрабатывать данный запрос.
     * Исключаем healthcheck запросы из обработки.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        return requestURI.endsWith(HEALTH_ENDPOINT) || requestURI.endsWith(HEALTH_ENDPOINT + "/");
    }

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
        String requestURI = request.getRequestURI();
        
        try {
            String jwt = extractJwtFromRequest(request);
            
            if (isValidJwtToken(jwt)) {
                setAuthenticationInContext(request, jwt);
            } else {
                logger.debug("JWT токен не найден или невалиден для запроса: " + request.getMethod() + " " + requestURI);
            }
        } catch (ExpiredJwtException ex) {
            // Истекший токен - это нормальная ситуация, логируем как debug
            logger.debug("JWT токен истек для запроса: " + request.getMethod() + " " + requestURI);
        } catch (Exception ex) {
            // Другие ошибки (невалидная подпись и т.д.) логируем как warning
            logger.warn("Не удалось установить аутентификацию пользователя в контексте безопасности: " + ex.getMessage());
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
        try {
            // Сначала проверяем истечение токена, чтобы избежать исключения при извлечении email
            if (tokenProvider.isTokenExpired(jwt)) {
                return false;
            }
            String email = tokenProvider.getEmailFromToken(jwt);
            return tokenProvider.validateToken(jwt, email);
        } catch (ExpiredJwtException ex) {
            return false;
        } catch (Exception ex) {
            logger.debug("Ошибка при проверке JWT токена: " + ex.getMessage());
            return false;
        }
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
