package ru.oparin.solution.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.UserRepository;
import ru.oparin.solution.util.EmailNormalizer;

import java.util.Collection;
import java.util.Collections;

/**
 * Сервис для загрузки данных пользователя для Spring Security.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Загрузка пользователя по email.
     *
     * @param email email пользователя
     * @return данные пользователя для Spring Security
     * @throws UserException если пользователь не найден
     */
    @Override
    public UserDetails loadUserByUsername(String email) {
        String normalized = EmailNormalizer.normalize(email);
        User user = userRepository.findByEmail(normalized)
                .orElseThrow(() -> {
                    log.warn("Пользователь не найден: {}", normalized);
                    return new UserException("Пользователь не найден: " + normalized, HttpStatus.NOT_FOUND);
                });

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                user.getIsActive(),
                true,
                true,
                true,
                getAuthorities(user.getRole())
        );
    }

    private Collection<? extends GrantedAuthority> getAuthorities(Role role) {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}

