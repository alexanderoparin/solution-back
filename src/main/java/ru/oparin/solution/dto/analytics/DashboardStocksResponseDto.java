package ru.oparin.solution.dto.analytics;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Актуальный срез остатков FBO/FBS для дашборда.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStocksResponseDto {

    private int total;
    private int fbo;
    private int fbs;
    private BigDecimal fboSharePercent;
    private BigDecimal fbsSharePercent;

    @Builder.Default
    private List<DashboardStockSizeDto> bySize = new ArrayList<>();

    /**
     * Время последнего обновления остатков по выборке (max updated_at).
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * Артикулы выборки (для селекта «Все артикулы» / конкретный nmId).
     */
    @Builder.Default
    private List<ArticleSummaryDto> articles = new ArrayList<>();
}
