package ru.oparin.solution.dto.cabinet;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Запрос на создание кабинета.
 */
@Getter
@Setter
public class CreateCabinetRequest {

    @NotBlank(message = "Название кабинета обязательно")
    @Size(min = 1, max = 255)
    private String name;
}
