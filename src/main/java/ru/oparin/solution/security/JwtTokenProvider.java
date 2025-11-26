package ru.oparin.solution.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

/**
 * Провайдер для работы с JWT токенами.
 */
@Component
public class JwtTokenProvider {

    private static final String USER_ID_CLAIM = "userId";
    private static final String ROLE_CLAIM = "role";

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private Long jwtExpiration;

    /**
     * Генерация JWT токена.
     *
     * @param email email пользователя
     * @param userId ID пользователя
     * @param role роль пользователя
     * @return JWT токен
     */
    public String generateToken(String email, Long userId, String role) {
        Date now = new Date();
        Date expiryDate = calculateExpiryDate(now);

        return Jwts.builder()
                .subject(email)
                .claim(USER_ID_CLAIM, userId)
                .claim(ROLE_CLAIM, role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Извлечение email из токена.
     *
     * @param token JWT токен
     * @return email пользователя
     */
    public String getEmailFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    /**
     * Извлечение ID пользователя из токена.
     *
     * @param token JWT токен
     * @return ID пользователя
     */
    public Long getUserIdFromToken(String token) {
        return getClaimFromToken(token, claims -> claims.get(USER_ID_CLAIM, Long.class));
    }

    /**
     * Извлечение роли из токена.
     *
     * @param token JWT токен
     * @return роль пользователя
     */
    public String getRoleFromToken(String token) {
        return getClaimFromToken(token, claims -> claims.get(ROLE_CLAIM, String.class));
    }

    /**
     * Получение даты истечения токена.
     *
     * @param token JWT токен
     * @return дата истечения
     */
    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    /**
     * Извлечение произвольного claim из токена.
     *
     * @param token JWT токен
     * @param claimsResolver функция для извлечения claim
     * @param <T> тип claim
     * @return значение claim
     */
    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Проверка истечения токена.
     *
     * @param token JWT токен
     * @return true если токен истек
     */
    public Boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }

    /**
     * Валидация токена.
     *
     * @param token JWT токен
     * @param email email пользователя
     * @return true если токен валиден
     */
    public Boolean validateToken(String token, String email) {
        final String tokenEmail = getEmailFromToken(token);
        return tokenEmail.equals(email) && !isTokenExpired(token);
    }

    /**
     * Получает ключ для подписи токена.
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Вычисляет дату истечения токена.
     */
    private Date calculateExpiryDate(Date now) {
        return new Date(now.getTime() + jwtExpiration);
    }

    /**
     * Получение всех claims из токена.
     *
     * @param token JWT токен
     * @return все claims
     */
    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
