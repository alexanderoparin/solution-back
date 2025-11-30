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
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeriodDto {
    private Integer id;
    private String name;
    
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateFrom;
    
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateTo;
}

