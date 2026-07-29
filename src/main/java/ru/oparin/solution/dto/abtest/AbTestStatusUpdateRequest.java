package ru.oparin.solution.dto.abtest;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import ru.oparin.solution.model.AbTestStatus;

/**
 * Запрос на включение/отключение А/Б-теста.
 */
@Data
public class AbTestStatusUpdateRequest {

    @NotNull
    private AbTestStatus status;
}
