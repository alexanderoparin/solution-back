package ru.oparin.solution.dto.cabinet;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Запрос на обновление кабинета (имя и/или API ключ).
 */
@Getter
@Setter
public class UpdateCabinetRequest {

    /**
     * Новое название кабинета (опционально).
     */
    @Size(min = 1, max = 255)
    private String name;

    /**
     * Новый WB API ключ (опционально). Если передан — обновляет ключ кабинета и сбрасывает статус валидации.
     */
    @Size(max = 500)
    private String apiKey;
}
