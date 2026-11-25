package ru.oparin.solution.dto.analytics;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Запрос товарных рейтингов дашборда.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardRankingsRequestDto {

    @Valid
    @NotEmpty
    @Size(min = 1, max = 5)
    private List<PeriodDto> periods;

    private Long sellerId;
    private Long cabinetId;

    @Builder.Default
    private List<Long> excludedNmIds = new ArrayList<>();

    @Builder.Default
    private List<Long> includedNmIds = new ArrayList<>();

    private Boolean filterToNone;
    private String search;
    private Boolean onlyWithPhoto;
    private Boolean onlyPriority;
    private Boolean onlyInAdvertising;
}
