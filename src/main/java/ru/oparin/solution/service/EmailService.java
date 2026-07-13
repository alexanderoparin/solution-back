package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import ru.oparin.solution.dto.LandingLeadSource;
import ru.oparin.solution.model.User;

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

    @Value("${app.brand-name:Click-I}")
    private String brandName;

    @Value("${app.mail.audit-to:corp@click-i.ru}")
    private String auditInboxEmail;

    /**
     * Отправляет письмо со ссылкой для сброса пароля.
     *
     * @param toEmail email получателя
     * @param token   токен сброса (подставляется в ссылку)
     */
    public void sendPasswordResetEmail(String toEmail, String token) {
        String resetLink = buildResetLink(token);
        String subject = "Восстановление пароля — " + brandName;
        String text = "Здравствуйте!\n\n"
                + "Вы запросили сброс пароля. Перейдите по ссылке для установки нового пароля:\n\n"
                + resetLink + "\n\n"
                + "Ссылка действительна 12 часов. Если вы не запрашивали сброс, проигнорируйте это письмо.\n\n"
                + "— " + brandName;

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
        String subject = "Подтверждение email — " + brandName;
        String text = "Здравствуйте!\n\n"
                + "Подтвердите, что это ваш email, перейдя по ссылке:\n\n"
                + confirmLink + "\n\n"
                + "Ссылка действительна 12 часов. Если вы не регистрировались в " + brandName + ", проигнорируйте это письмо.\n\n"
                + "— " + brandName;

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

    /**
     * Отправляет заявку на аудит рекламного кабинета на почту оператора.
     *
     * @param name           имя заявителя
     * @param telegram       Telegram для связи
     * @param additionalInfo дополнительная информация (может быть пустой)
     * @param source         кнопка/блок лендинга, с которого отправлена заявка
     */
    public void sendCabinetAuditRequestEmail(
            String name,
            String telegram,
            String additionalInfo,
            LandingLeadSource source
    ) {
        String subject = "Заявка на аудит — " + source.getLabel() + " — " + brandName;
        String text = "Новая заявка на аудит рекламного кабинета с лендинга.\n\n"
                + buildLandingLeadEmailBody(name, telegram, additionalInfo, source);
        sendLandingInboxEmail(subject, text, "аудит кабинета");
    }

    /**
     * Отправляет заявку на консультацию по ведению рекламных кабинетов.
     *
     * @param name           имя заявителя
     * @param telegram       Telegram для связи
     * @param additionalInfo дополнительная информация (может быть пустой)
     * @param source         кнопка/блок лендинга, с которого отправлена заявка
     */
    public void sendAgencyConsultationRequestEmail(
            String name,
            String telegram,
            String additionalInfo,
            LandingLeadSource source
    ) {
        String subject = "Заявка на консультацию — " + source.getLabel() + " — " + brandName;
        String text = "Новая заявка на консультацию по ведению рекламных кабинетов с лендинга.\n\n"
                + buildLandingLeadEmailBody(name, telegram, additionalInfo, source);
        sendLandingInboxEmail(subject, text, "консультацию по ведению кабинетов");
    }

    private String buildLandingLeadEmailBody(
            String name,
            String telegram,
            String additionalInfo,
            LandingLeadSource source
    ) {
        StringBuilder body = new StringBuilder();
        body.append("Источник: ").append(source.getLabel()).append('\n');
        body.append("Имя: ").append(name).append('\n');
        body.append("Telegram: ").append(telegram).append('\n');
        if (additionalInfo != null && !additionalInfo.isBlank()) {
            body.append("Дополнительная информация:\n").append(additionalInfo.trim()).append('\n');
        }
        body.append("\n— ").append(brandName);
        return body.toString();
    }

    private void sendLandingInboxEmail(String subject, String text, String requestLabel) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(auditInboxEmail);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            log.info("Заявка на {} отправлена на {}", requestLabel, auditInboxEmail);
        } catch (MailAuthenticationException e) {
            log.error("Ошибка SMTP-аутентификации при отправке заявки на {}: {}", requestLabel, e.getMessage());
            throw new RuntimeException("Не удалось отправить запрос. Попробуйте позже.", e);
        } catch (Exception e) {
            log.error("Ошибка отправки заявки на {}: {}", requestLabel, e.getMessage(), e);
            throw new RuntimeException("Не удалось отправить запрос. Попробуйте позже.", e);
        }
    }

    /**
     * Письмо-приглашение в кабинет Clicki.
     */
    public void sendCabinetInvitationEmail(String toEmail, User inviter, String cabinetName, String token) {
        String inviteLink = buildInviteLink(token);
        String inviterLabel = inviter.getName() != null && !inviter.getName().isBlank()
                ? inviter.getName()
                : inviter.getEmail();
        String subject = "Вас пригласили в " + brandName;
        String text = "Здравствуйте!\n\n"
                + "Пользователь " + inviterLabel + " предоставил вам доступ к системе " + brandName
                + " (кабинет «" + cabinetName + "»).\n\n"
                + "Для активации доступа зарегистрируйтесь или войдите в существующий аккаунт:\n\n"
                + inviteLink + "\n\n"
                + "Если вы не ожидали это приглашение, просто проигнорируйте письмо.\n\n"
                + "С уважением,\nКоманда " + brandName;
        sendSimple(toEmail, subject, text, "приглашение в кабинет");
    }

    private void sendSimple(String toEmail, String subject, String text, String context) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            log.info("Письмо ({}) отправлено на {}", context, toEmail);
        } catch (MailAuthenticationException e) {
            log.error("Ошибка SMTP при отправке ({}) на {}: {}", context, toEmail, e.getMessage());
            throw new RuntimeException("Не удалось отправить письмо. Попробуйте позже.", e);
        } catch (Exception e) {
            log.error("Ошибка отправки ({}) на {}: {}", context, toEmail, e.getMessage(), e);
            throw new RuntimeException("Не удалось отправить письмо. Попробуйте позже.", e);
        }
    }

    private String buildInviteLink(String token) {
        String base = frontendUrl.replaceAll("/$", "");
        return base + "/invite/" + token;
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
