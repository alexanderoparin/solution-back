package ru.oparin.solution.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO для регистрации продавца.
 */
@Getter
@Setter
public class RegisterRequest {

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
}

