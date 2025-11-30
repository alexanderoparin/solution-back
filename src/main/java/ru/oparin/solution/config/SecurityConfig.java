package ru.oparin.solution.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.security.JwtAuthenticationFilter;
import ru.oparin.solution.security.CustomUserDetailsService;

import java.util.Arrays;
import java.util.List;

/**
 * Конфигурация Spring Security.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final int BCRYPT_STRENGTH = 12;
    private static final String FRONTEND_URL = "http://localhost:5173";
    private static final String AUTH_ENDPOINTS = "/auth/**";
    private static final String HEALTH_ENDPOINT = "/health";
    private static final String ADMIN_ENDPOINTS = "/admin/**";
    private static final String SELLER_ENDPOINTS = "/seller/**";
    private static final String WORKER_ENDPOINTS = "/worker/**";
    private static final String ANALYTICS_ENDPOINTS = "/analytics/**";
    public static final String ADMIN = Role.ADMIN.name();
    public static final String SELLER = Role.SELLER.name();
    public static final String WORKER = Role.WORKER.name();

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Bean для кодирования паролей (BCrypt).
     *
     * @return password encoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    /**
     * Провайдер аутентификации.
     *
     * @return authentication provider
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * Менеджер аутентификации.
     *
     * @param authConfig конфигурация аутентификации
     * @return authentication manager
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * Цепочка фильтров безопасности.
     *
     * @param http HTTP security
     * @return security filter chain
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> configureAuthorization(auth))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Конфигурация CORS.
     *
     * @return CORS configuration source
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = createCorsConfiguration();
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Настраивает правила авторизации для различных эндпоинтов.
     */
    private void configureAuthorization(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth
    ) {
        auth
                .requestMatchers(AUTH_ENDPOINTS, HEALTH_ENDPOINT).permitAll()
                .requestMatchers(ADMIN_ENDPOINTS).hasRole(ADMIN)
                .requestMatchers(SELLER_ENDPOINTS).hasAnyRole(ADMIN, SELLER)
                .requestMatchers(WORKER_ENDPOINTS).hasAnyRole(ADMIN, SELLER, WORKER)
                .requestMatchers(ANALYTICS_ENDPOINTS).hasAnyRole(ADMIN, SELLER)
                .anyRequest().authenticated();
    }

    /**
     * Создает конфигурацию CORS.
     */
    private CorsConfiguration createCorsConfiguration() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(FRONTEND_URL));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        return configuration;
    }
}
