package ru.oparin.solution.dto.analytics;

import lombok.*;

import java.math.BigDecimal;

/**
 * Одна строка агрегированной статистики по поисковому кластеру за выбранный период.
 * <p>
 * Используется в {@link NormQueryClustersResponseDto} для строки «Всего» и для списка кластеров.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NormQueryClusterRowDto {

    /**
     * Название поискового кластера. Для итоговой строки может быть {@code null}.
     */
    private String normQuery;

    /**
     * Средняя позиция (взвешенная по кликам при агрегации за период).
     */
    private BigDecimal avgPos;

    /**
     * Сумма кликов за период.
     */
    private Integer clicks;

    /**
     * Сумма добавлений в корзину за период.
     */
    private Integer atbs;

    /**
     * Сумма заказов за период.
     */
    private Integer orders;

    /**
     * Сумма затрат, руб.
     */
    private BigDecimal spend;

    /**
     * Средняя стоимость клика за период ({@code spend / clicks}), руб.
     */
    private BigDecimal cpc;
}
