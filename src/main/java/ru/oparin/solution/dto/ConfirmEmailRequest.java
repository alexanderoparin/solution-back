package ru.oparin.solution.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Запрос на подтверждение email по токену из ссылки.
 */
@Getter
@Setter
public class ConfirmEmailRequest {

    @NotBlank(message = "Токен обязателен")
    private String token;
}
