package ru.oparin.solution.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Постраничный список рекламных кампаний кабинета.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignPageResponse {

    /** Кампании текущей страницы. */
    private List<CampaignDto> content;

    /** Число кампаний после фильтров. */
    private long totalElements;

    /** Число страниц. */
    private int totalPages;

    /** Размер страницы. */
    private int size;

    /** Номер страницы, с нуля. */
    private int number;

    /**
     * Типы РК кабинета для фильтра (без учёта выбранного типа/поиска/статуса).
     */
    private List<String> types;
}
