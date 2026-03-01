package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.PasswordResetToken;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.PasswordResetTokenRepository;
import ru.oparin.solution.repository.UserRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Сервис восстановления пароля: создание токена, отправка письма, сброс по токену.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private static final int TOKEN_VALID_HOURS = 24;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final UserService userService;

    /**
     * Инициирует сброс пароля: создаёт токен и отправляет письмо на email.
     * Если пользователя нет — не раскрываем это (всегда возвращаем успех).
     *
     * @param email email пользователя
     */
    @Transactional
    public void forgotPassword(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.debug("Запрос сброса пароля для несуществующего email: {}", email);
            return;
        }
        User user = userOpt.get();
        tokenRepository.deleteByUser_Id(user.getId());

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(TOKEN_VALID_HOURS * 3600L);
        String token = UUID.randomUUID().toString().replace("-", "");

        PasswordResetToken entity = PasswordResetToken.builder()
                .user(user)
                .token(token)
                .expiresAt(expiresAt)
                .createdAt(now)
                .build();
        tokenRepository.save(entity);

        emailService.sendPasswordResetEmail(user.getEmail(), token);
        log.info("Токен сброса пароля создан для пользователя {}", user.getEmail());
    }

    /**
     * Сбрасывает пароль по токену из письма.
     *
     * @param token       токен из ссылки
     * @param newPassword новый пароль
     * @throws UserException если токен не найден или истёк
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        Instant now = Instant.now();
        PasswordResetToken resetToken = tokenRepository.findByTokenAndExpiresAtAfter(token, now)
                .orElseThrow(() -> new UserException(
                        "Ссылка для сброса пароля недействительна или истекла. Запросите новую.",
                        HttpStatus.BAD_REQUEST
                ));

        User user = resetToken.getUser();
        userService.setPassword(user.getId(), newPassword);
        tokenRepository.delete(resetToken);
        log.info("Пароль успешно сброшен для пользователя {}", user.getEmail());
    }
}
