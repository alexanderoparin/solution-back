package ru.oparin.solution.dto.hypothesis;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Создание / обновление гипотезы.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbHypothesisUpsertRequest {

    @NotNull
    private Long nmId;

    @NotBlank
    private String title;

    private String description;

    @NotNull
    private LocalDate checkFrom;

    @NotNull
    private LocalDate checkTo;

    @NotBlank
    private String workflowStatus;

    @NotEmpty
    private List<String> criteria;
}
