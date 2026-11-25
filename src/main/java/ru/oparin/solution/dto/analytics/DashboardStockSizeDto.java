package ru.oparin.solution.dto.analytics;

import lombok.*;

/**
 * Остатки по одному размеру (FBO/FBS/итого).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStockSizeDto {

    private String size;
    private int fbo;
    private int fbs;
    private int total;
}
