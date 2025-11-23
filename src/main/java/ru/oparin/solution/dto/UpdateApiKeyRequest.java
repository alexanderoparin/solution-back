package ru.oparin.solution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO для обновления WB API ключа.
 */
@Getter
@Setter
public class UpdateApiKeyRequest {

    /**
     * Новый WB API ключ.
     */
    @NotBlank(message = "WB API ключ обязателен")
    @Size(min = 10, message = "WB API ключ должен содержать минимум 10 символов")
    private String wbApiKey;
}

