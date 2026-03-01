package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.frontend-url:https://wb-solution.ru}")
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
        } catch (Exception e) {
            log.error("Ошибка отправки письма на {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Не удалось отправить письмо. Попробуйте позже.", e);
        }
    }

    private String buildResetLink(String token) {
        String base = frontendUrl.replaceAll("/$", "");
        return base + "/reset-password?token=" + token;
    }
}
