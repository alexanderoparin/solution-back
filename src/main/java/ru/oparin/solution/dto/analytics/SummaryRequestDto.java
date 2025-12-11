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
 * Используется для получения агрегированных данных по всем артикулам за указанные периоды.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SummaryRequestDto {
    /**
     * Список периодов для анализа. Должен содержать от 1 до 10 периодов.
     */
    @Valid
    @NotEmpty(message = "Периоды не могут быть пустыми")
    @Size(min = 1, max = 10, message = "Количество периодов должно быть от 1 до 10")
    private List<PeriodDto> periods;
    
    /**
     * Список артикулов (nmId), которые нужно исключить из анализа.
     */
    @Builder.Default
    private List<Long> excludedNmIds = new ArrayList<>();

    /**
     * ID селлера для просмотра аналитики (опционально, только для ADMIN/MANAGER).
     * Если не указан, используется последний активный селлер.
     */
    private Long sellerId;
}

