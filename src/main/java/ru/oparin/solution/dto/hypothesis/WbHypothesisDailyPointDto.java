package ru.oparin.solution.dto.hypothesis;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Точка динамики метрик гипотезы.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbHypothesisDailyPointDto {
    private LocalDate date;
    private String period; // BASELINE | CHECK
    private Map<String, BigDecimal> values;
}
