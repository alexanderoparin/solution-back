package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Сервис отправки писем (восстановление пароля и т.д.).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromEmail;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * Отправляет письмо со ссылкой для сброса пароля.
     *
     * @param toEmail email получателя
     * @param token   токен сброса (подставляется в ссылку)
     */
    public void sendPasswordResetEmail(String toEmail, String token) {
        String resetLink = buildResetLink(token);
        String subject = "Восстановление пароля — WB-Solution";
        String text = "Здравствуйте!\n\n"
                + "Вы запросили сброс пароля. Перейдите по ссылке для установки нового пароля:\n\n"
                + resetLink + "\n\n"
                + "Ссылка действительна 24 часа. Если вы не запрашивали сброс, проигнорируйте это письмо.\n\n"
                + "— WB-Solution";

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            log.info("Письмо для сброса пароля отправлено на {}", toEmail);
        } catch (MailAuthenticationException e) {
            log.error("Ошибка SMTP-аутентификации при отправке на {}: {}. Проверьте MAIL_PASSWORD и настройки почты (smtp.timeweb.ru).", toEmail, e.getMessage());
            throw new RuntimeException("Не удалось отправить письмо. Попробуйте позже.", e);
        } catch (Exception e) {
            log.error("Ошибка отправки письма на {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Не удалось отправить письмо. Попробуйте позже.", e);
        }
    }

    /**
     * Отправляет письмо со ссылкой для подтверждения email.
     *
     * @param toEmail email получателя
     * @param token   токен подтверждения (подставляется в ссылку)
     */
    public void sendEmailConfirmationEmail(String toEmail, String token) {
        String confirmLink = buildConfirmEmailLink(token);
        String subject = "Подтверждение email — WB-Solution";
        String text = "Здравствуйте!\n\n"
                + "Подтвердите, что это ваш email, перейдя по ссылке:\n\n"
                + confirmLink + "\n\n"
                + "Ссылка действительна 24 часа. Если вы не регистрировались в WB-Solution, проигнорируйте это письмо.\n\n"
                + "— WB-Solution";

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            log.info("Письмо для подтверждения email отправлено на {}", toEmail);
        } catch (MailAuthenticationException e) {
            log.error("Ошибка SMTP-аутентификации при отправке на {}: {}. Проверьте MAIL_PASSWORD и настройки почты (smtp.timeweb.ru).", toEmail, e.getMessage());
            throw new RuntimeException("Не удалось отправить письмо. Попробуйте позже.", e);
        } catch (Exception e) {
            log.error("Ошибка отправки письма на {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Не удалось отправить письмо. Попробуйте позже.", e);
        }
    }

    private String buildResetLink(String token) {
        String base = frontendUrl.replaceAll("/$", "");
        return base + "/reset-password?token=" + token;
    }

    private String buildConfirmEmailLink(String token) {
        String base = frontendUrl.replaceAll("/$", "");
        return base + "/confirm-email?token=" + token;
    }
}
