package ru.oparin.solution.service.events.payload;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

@Builder
public record PromotionAdvertsBatchPayload(
        List<Long> campaignIds,
        int batchIndex,
        LocalDate dateFrom,
        LocalDate dateTo,
        boolean includeStocks
) {
}
