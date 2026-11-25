package ru.oparin.solution.dto.analytics;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Запрос складского среза для дашборда (без периодов продаж).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStocksRequestDto {

    private Long sellerId;
    private Long cabinetId;

    /**
     * Если задан — остатки только этого артикула (внутри уже отфильтрованной выборки).
     */
    private Long nmId;

    /**
     * ALL (по умолчанию), FBO или FBS.
     */
    private String fulfillment;

    @Builder.Default
    private List<Long> excludedNmIds = new ArrayList<>();

    @Builder.Default
    private List<Long> includedNmIds = new ArrayList<>();

    private Boolean filterToNone;
    private String search;
    private Boolean onlyWithPhoto;
    private Boolean onlyPriority;
    private Boolean onlyInAdvertising;
}
