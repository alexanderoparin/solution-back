package ru.oparin.solution.dto.analytics;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * DTO для периода аналитики.
 * Определяет временной интервал для агрегации данных.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeriodDto {
    /**
     * Уникальный идентификатор периода.
     */
    private Integer id;
    
    /**
     * Название периода (например, "Период 1", "Период 2").
     */
    private String name;
    
    /**
     * Дата начала периода (включительно).
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateFrom;
    
    /**
     * Дата окончания периода (включительно).
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateTo;
}

