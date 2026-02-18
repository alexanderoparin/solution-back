package ru.oparin.solution.dto.analytics;

import lombok.*;

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
     * Участвует ли товар в акции WB (календарь акций и/или скидка продавца), не путать с рекламными кампаниями.
     */
    private Boolean inWbPromotion;

    /**
     * Названия акций календаря WB, в которых участвует товар (для тултипа «В акции»).
     */
    private List<String> wbPromotionNames;

    /**
     * Остатки товара на складах на текущий момент.
     */
    private List<StockDto> stocks;

    /**
     * Товары «в связке» (доп. товары для отображения рядом с артикулом).
     */
    private List<ArticleSummaryDto> bundleProducts;
}

