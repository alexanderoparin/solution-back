package ru.oparin.solution.dto.hypothesis;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Ручная установка итога гипотезы.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbHypothesisVerdictRequest {

    @NotBlank
    private String verdict;
}
