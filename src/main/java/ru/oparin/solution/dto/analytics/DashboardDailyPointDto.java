package ru.oparin.solution.dto.analytics;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Точка дневного ряда для дашборда.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDailyPointDto {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    /**
     * Суммарное значение метрики за день по отфильтрованным артикулам.
     */
    private BigDecimal value;
}
