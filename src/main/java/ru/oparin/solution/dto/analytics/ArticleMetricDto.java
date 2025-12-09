package ru.oparin.solution.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * DTO для метрики конкретного артикула.
 * Используется для отображения метрик по артикулу в разрезе периодов.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleMetricDto {
    /**
     * Артикул WB (nmID).
     */
    private Long nmId;
    
    /**
     * URL миниатюры первой фотографии товара.
     */
    private String photoTm;
    
    /**
     * Список значений метрики по периодам.
     */
    private List<PeriodMetricValueDto> periods;
}

