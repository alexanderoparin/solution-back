package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.EmailConfirmationToken;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.EmailConfirmationTokenRepository;
import ru.oparin.solution.repository.UserRepository;

import java.time.Instant;
import java.util.UUID;

/**
 * Сервис подтверждения email: отправка письма (не чаще 1 раза в 24 ч) и подтверждение по токену.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailConfirmationService {

    private static final int TOKEN_VALID_HOURS = 24;
    private static final int RESEND_COOLDOWN_HOURS = 24;

    private final UserRepository userRepository;
    private final EmailConfirmationTokenRepository tokenRepository;
    private final EmailService emailService;

    /**
     * Отправляет письмо для подтверждения email текущему пользователю.
     * Только для сторонних селлеров с неподтверждённой почтой.
     * Повторная отправка не чаще 1 раза в 24 часа.
     *
     * @param user текущий пользователь
     * @throws UserException если не селлер, клиент агентства, почта уже подтверждена или письмо уже отправлялось менее 24 ч назад
     */
    @Transactional
    public void sendConfirmationEmail(User user) {
        if (user.getRole() != Role.SELLER) {
            throw new UserException("Подтверждение почты доступно только для продавцов", HttpStatus.FORBIDDEN);
        }
        if (Boolean.TRUE.equals(user.getIsAgencyClient())) {
            throw new UserException("Клиентам агентства подтверждение почты не требуется", HttpStatus.BAD_REQUEST);
        }
        if (Boolean.TRUE.equals(user.getEmailConfirmed())) {
            throw new UserException("Почта уже подтверждена", HttpStatus.BAD_REQUEST);
        }

        Instant now = Instant.now();
        Instant cooldownUntil = user.getLastEmailConfirmationSentAt() != null
                ? user.getLastEmailConfirmationSentAt().plusSeconds(RESEND_COOLDOWN_HOURS * 3600L)
                : null;
        if (cooldownUntil != null && now.isBefore(cooldownUntil)) {
            long secondsLeft = cooldownUntil.getEpochSecond() - now.getEpochSecond();
            long hoursLeft = secondsLeft / 3600;
            long minutesLeft = (secondsLeft % 3600) / 60;
            String timeLeft = hoursLeft > 0
                    ? hoursLeft + " ч " + (minutesLeft > 0 ? minutesLeft + " мин" : "")
                    : minutesLeft + " мин";
            throw new UserException(
                    "Письмо для подтверждения уже было отправлено. Повторная отправка возможна через " + timeLeft + ".",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }

        tokenRepository.deleteByUser_Id(user.getId());
        Instant expiresAt = now.plusSeconds(TOKEN_VALID_HOURS * 3600L);
        String token = UUID.randomUUID().toString().replace("-", "");

        EmailConfirmationToken entity = EmailConfirmationToken.builder()
                .user(user)
                .token(token)
                .expiresAt(expiresAt)
                .createdAt(now)
                .build();
        tokenRepository.save(entity);

        user.setLastEmailConfirmationSentAt(now);
        userRepository.save(user);

        emailService.sendEmailConfirmationEmail(user.getEmail(), token);
        log.info("Письмо для подтверждения email отправлено пользователю {}", user.getEmail());
    }

    /**
     * Подтверждает email по токену из ссылки.
     *
     * @param token токен из ссылки
     * @throws UserException если токен не найден или истёк
     */
    @Transactional
    public void confirmEmail(String token) {
        Instant now = Instant.now();
        EmailConfirmationToken confirmationToken = tokenRepository.findByTokenAndExpiresAtAfter(token, now)
                .orElseThrow(() -> new UserException(
                        "Ссылка для подтверждения недействительна или истекла. Запросите новое письмо в профиле.",
                        HttpStatus.BAD_REQUEST
                ));

        User user = confirmationToken.getUser();
        user.setEmailConfirmed(true);
        userRepository.save(user);
        tokenRepository.delete(confirmationToken);
        log.info("Email подтверждён для пользователя {}", user.getEmail());
    }
}
