package ru.oparin.solution.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
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
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
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
            "https://click-i.ru",
            "https://www.click-i.ru",
            "http://click-i.ru",
            "https://wb-solution.ru",
            "http://wb-solution.ru",
            "http://109.68.213.220",
            "http://109.68.213.220:80"
    );
    private static final String AUTH_ENDPOINTS = "/auth/**";
    private static final String HEALTH_ENDPOINT = "/health";
    private static final String ADMIN_ENDPOINTS = "/admin/**";
    private static final String ANALYTICS_ENDPOINTS = "/analytics/**";
    private static final String ADVERTISING_ENDPOINTS = "/advertising/**";
    private static final String USERS_MANAGEMENT_ENDPOINTS = "/users/**";
    private static final String USERS_TRIGGER_STOCKS_UPDATE_ENDPOINT = "/users/cabinets/*/trigger-stocks-update";
    private static final String CABINETS_ENDPOINTS = "/cabinets/**";
    private static final String INVITE_ENDPOINTS = "/public/invitations/**";
    private static final String SUBSCRIPTION_PLANS = "/subscription/plans";
    private static final String SUBSCRIPTION_STATUS = "/subscription/status";
    private static final String PUBLIC_ENDPOINTS = "/public/**";
    private static final String WEBHOOKS = "/webhooks/**";
    public static final String ADMIN = Role.ADMIN.name();
    public static final String USER = Role.USER.name();

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final EmailConfirmationAccessFilter emailConfirmationAccessFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(this::configureAuthorization)
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(emailConfirmationAccessFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = createCorsConfiguration();
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private void configureAuthorization(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth
    ) {
        auth
                .requestMatchers(AUTH_ENDPOINTS, HEALTH_ENDPOINT).permitAll()
                .requestMatchers(INVITE_ENDPOINTS).permitAll()
                .requestMatchers(SUBSCRIPTION_PLANS, SUBSCRIPTION_STATUS).permitAll()
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                .requestMatchers(WEBHOOKS).permitAll()
                .requestMatchers(ADMIN_ENDPOINTS).hasRole(ADMIN)
                .requestMatchers(ANALYTICS_ENDPOINTS).hasAnyRole(ADMIN, USER)
                .requestMatchers(ADVERTISING_ENDPOINTS).hasAnyRole(ADMIN, USER)
                .requestMatchers(USERS_TRIGGER_STOCKS_UPDATE_ENDPOINT).hasAnyRole(ADMIN, USER)
                .requestMatchers(USERS_MANAGEMENT_ENDPOINTS).hasRole(ADMIN)
                .requestMatchers(CABINETS_ENDPOINTS).hasAnyRole(ADMIN, USER)
                .anyRequest().authenticated();
    }

    private CorsConfiguration createCorsConfiguration() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(ALLOWED_ORIGINS);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        return configuration;
    }
}
