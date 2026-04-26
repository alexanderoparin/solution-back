package ru.oparin.solution.dto.cabinet;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import ru.oparin.solution.model.CabinetTokenType;

/**
 * Запрос на создание кабинета.
 */
@Getter
@Setter
public class CreateCabinetRequest {

    @Size(min = 1, max = 255)
    private String name;

    @Size(min = 1, max = 500)
    private String apiKey;

    /**
     * Тип WB API токена. По умолчанию BASIC.
     */
    private CabinetTokenType tokenType;
}
