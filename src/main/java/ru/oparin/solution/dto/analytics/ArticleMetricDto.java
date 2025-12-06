package ru.oparin.solution.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * DTO для метрики конкретного артикула.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleMetricDto {
    private Long nmId;
    private String photoTm; // URL миниатюры первой фотографии товара
    private List<PeriodMetricValueDto> periods;
}

