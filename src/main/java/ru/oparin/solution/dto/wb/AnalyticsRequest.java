package ru.oparin.solution.dto.wb;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * DTO для запроса аналитики воронки продаж.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsRequest {

    /**
     * Период начала аналитики.
     */
    @NotNull(message = "Дата начала периода обязательна")
    private LocalDate dateFrom;

    /**
     * Период окончания аналитики.
     */
    @NotNull(message = "Дата окончания периода обязательна")
    private LocalDate dateTo;
}

