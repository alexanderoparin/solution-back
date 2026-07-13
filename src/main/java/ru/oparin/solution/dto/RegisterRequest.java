package ru.oparin.solution.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import ru.oparin.solution.model.AccountType;

import java.util.List;

/**
 * DTO для регистрации пользователя.
 */
@Getter
@Setter
public class RegisterRequest {

    /**
     * Имя пользователя.
     */
    @NotBlank(message = "Имя обязательно")
    @Size(max = 255, message = "Имя слишком длинное")
    private String name;

    /**
     * Email пользователя.
     */
    @NotBlank(message = "Email обязателен")
    @Email(message = "Некорректный формат email")
    private String email;

    /**
     * Пароль пользователя.
     */
    @NotBlank(message = "Пароль обязателен")
    @Size(min = 6, message = "Пароль должен содержать минимум 6 символов")
    private String password;

    /**
     * Согласие с офертой и политикой конфиденциальности (обязательно для регистрации).
     */
    @AssertTrue(message = "Необходимо согласие с условиями оферты и политикой конфиденциальности")
    private Boolean agreeToOffer;

    /**
     * Согласие на получение информационных и маркетинговых сообщений (необязательно).
     */
    private Boolean marketingConsent;

    /**
     * Типы аккаунта (минимум один).
     */
    @NotEmpty(message = "Укажите тип аккаунта")
    private List<AccountType> accountTypes;

    /**
     * Токен приглашения (если регистрация по invite).
     */
    private String invitationToken;
}

