package ru.oparin.solution.dto.analytics;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Запрос дневной динамики для страницы «Дашборды».
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDailyRequestDto {

    @Valid
    @NotEmpty
    @Size(min = 1, max = 5)
    private List<PeriodDto> periods;

    /**
     * Метрика: orders, ordersAmount, transitions, cart, costs, clicks, views.
     */
    @NotBlank
    private String metric;

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
