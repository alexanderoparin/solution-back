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
import ru.oparin.solution.security.CustomUserDetailsService;
import ru.oparin.solution.security.EmailConfirmationAccessFilter;
import ru.oparin.solution.security.JwtAuthenticationFilter;

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
    private static final List<String> ALLOWED_ORIGINS = List.of(
            "http://localhost:5173",
            "https://wb-solution.ru",
            "http://wb-solution.ru",
            "http://109.68.213.220",
            "http://109.68.213.220:80"
    );
    private static final String AUTH_ENDPOINTS = "/auth/**";
    private static final String HEALTH_ENDPOINT = "/health";
    private static final String ADMIN_ENDPOINTS = "/admin/**";
    private static final String SELLER_ENDPOINTS = "/seller/**";
    private static final String WORKER_ENDPOINTS = "/worker/**";
    private static final String ANALYTICS_ENDPOINTS = "/analytics/**";
    private static final String ADVERTISING_ENDPOINTS = "/advertising/**";
    private static final String USERS_MANAGEMENT_ENDPOINTS = "/users/**";
    private static final String USERS_TRIGGER_STOCKS_UPDATE_ENDPOINT = "/users/cabinets/*/trigger-stocks-update";
    private static final String CABINETS_ENDPOINTS = "/cabinets/**";
    private static final String SUBSCRIPTION_PAYMENT_RESULT = "/subscription/payment/result";
    private static final String SUBSCRIPTION_PLANS = "/subscription/plans";
    private static final String SUBSCRIPTION_STATUS = "/subscription/status";
    public static final String ADMIN = Role.ADMIN.name();
    public static final String MANAGER = Role.MANAGER.name();
    public static final String SELLER = Role.SELLER.name();
    public static final String WORKER = Role.WORKER.name();

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final EmailConfirmationAccessFilter emailConfirmationAccessFilter;

    /**
     * Bean для кодирования паролей (BCrypt).
     *
     * @return кодировщик паролей
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    /**
     * Провайдер аутентификации.
     *
     * @return провайдер аутентификации
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
     * @return менеджер аутентификации
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * Цепочка фильтров безопасности.
     *
     * @param http конфигурация HTTP-безопасности
     * @return цепочка фильтров безопасности
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(this::configureAuthorization)
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(emailConfirmationAccessFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Конфигурация CORS.
     *
     * @return источник конфигурации CORS
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
                .requestMatchers(SUBSCRIPTION_PAYMENT_RESULT, SUBSCRIPTION_PLANS, SUBSCRIPTION_STATUS).permitAll()
                .requestMatchers(ADMIN_ENDPOINTS).hasRole(ADMIN)
                .requestMatchers(SELLER_ENDPOINTS).hasAnyRole(ADMIN, SELLER)
                .requestMatchers(WORKER_ENDPOINTS).hasAnyRole(ADMIN, SELLER, WORKER)
                .requestMatchers(ANALYTICS_ENDPOINTS).hasAnyRole(ADMIN, MANAGER, SELLER, WORKER)
                .requestMatchers(ADVERTISING_ENDPOINTS).hasAnyRole(ADMIN, MANAGER, SELLER, WORKER)
                .requestMatchers(USERS_TRIGGER_STOCKS_UPDATE_ENDPOINT).hasAnyRole(ADMIN, MANAGER, SELLER, WORKER)
                .requestMatchers(USERS_MANAGEMENT_ENDPOINTS).hasAnyRole(ADMIN, MANAGER, SELLER)
                .requestMatchers(CABINETS_ENDPOINTS).hasAnyRole(SELLER, WORKER)
                .anyRequest().authenticated();
    }

    /**
     * Создает конфигурацию CORS.
     */
    private CorsConfiguration createCorsConfiguration() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(ALLOWED_ORIGINS);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        return configuration;
    }
}
