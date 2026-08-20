package ru.oparin.solution.dto.abtest;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import ru.oparin.solution.model.WbAbTestStatus;

/**
 * Запрос на включение/отключение А/Б-теста.
 */
@Data
public class WbAbTestStatusUpdateRequest {

    @NotNull
    private WbAbTestStatus status;
}
