package ru.oparin.solution.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * DTO для ответа страницы артикула.
 * Содержит всю информацию, необходимую для отображения страницы детального просмотра артикула.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleResponseDto {
    /**
     * Детальная информация об артикуле.
     */
    private ArticleDetailDto article;
    
    /**
     * Список периодов аналитики.
     */
    private List<PeriodDto> periods;
    
    /**
     * Список всех метрик (13 метрик: общая воронка, рекламная воронка, ценообразование).
     */
    private List<MetricDto> metrics;
    
    /**
     * Ежедневные данные за последние 14 дней.
     */
    private List<DailyDataDto> dailyData;
    
    /**
     * Список рекламных кампаний, в которых участвует артикул.
     */
    private List<CampaignDto> campaigns;
    
    /**
     * Остатки товара на складах на текущий момент.
     */
    private List<StockDto> stocks;
}

