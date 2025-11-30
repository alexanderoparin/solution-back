package ru.oparin.solution.dto.analytics;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO для запроса сводной аналитики.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SummaryRequestDto {
    
    @Valid
    @NotEmpty(message = "Периоды не могут быть пустыми")
    @Size(min = 1, max = 10, message = "Количество периодов должно быть от 1 до 10")
    private List<PeriodDto> periods;
    
    @Builder.Default
    private List<Long> excludedNmIds = new ArrayList<>();
}

