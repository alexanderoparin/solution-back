package ru.oparin.solution.dto.hypothesis;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

/**
 * Массовое удаление гипотез.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbHypothesisBulkDeleteRequest {

    @NotEmpty
    private List<Long> ids;
}
