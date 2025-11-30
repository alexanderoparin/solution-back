package ru.oparin.solution.dto.analytics;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * DTO для ежедневных данных по артикулу.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyDataDto {
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;
    private Integer transitions;
    private Integer cart;
    private Integer orders;
}

