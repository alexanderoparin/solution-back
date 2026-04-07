package ru.oparin.solution.dto.analytics;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateArticlePriorityRequest {

    @NotNull(message = "Флаг приоритета обязателен")
    private Boolean priority;
}
