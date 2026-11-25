package ru.oparin.solution.dto.analytics;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Ответ дневной динамики дашборда.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDailyResponseDto {

    private String metric;

    @Builder.Default
    private List<DashboardDailyPointDto> points = new ArrayList<>();
}
