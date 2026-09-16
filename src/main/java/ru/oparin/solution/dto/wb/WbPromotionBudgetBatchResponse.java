package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;

/**
 * Ответ POST /api/advert/v2/budget. {@code adverts} может быть {@code null} или пустым,
 * если ни одна РК не в статусах 4 / 9 / 11.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WbPromotionBudgetBatchResponse {

    /** Остатки по запрошенным кампаниям (могут отсутствовать для завершённых). */
    private List<AdvertBudget> adverts;

    /**
     * Остаток бюджета одной кампании.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AdvertBudget {

        /** ID кампании WB. */
        private Long advertId;

        /** Валюта кабинета, ISO 4217. */
        private String currency;

        /** Остаток бюджета, ₽. */
        private Integer total;
    }
}
