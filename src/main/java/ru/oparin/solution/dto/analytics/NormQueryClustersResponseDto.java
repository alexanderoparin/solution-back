package ru.oparin.solution.dto.analytics;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ответ REST API: статистика по поисковым кластерам рекламной кампании за период.
 * <p>
 * Эндпоинт: {@code GET /advertising/campaigns/{id}/normquery-clusters}.
 * Данные читаются из {@code promotion_norm_query_statistics} после синхронизации с WB.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NormQueryClustersResponseDto {

    /**
     * Итоги по всем кластерам за период (аналог «Всего по топ кластерам» в ЛК WB).
     */
    private NormQueryClusterRowDto totals;

    /**
     * Страница кластеров (размер задаётся параметром {@code size}, по умолчанию 20).
     */
    private List<NormQueryClusterRowDto> rows;

    /** Общее число кластеров с учётом поиска. */
    private long totalElements;

    /** Номер страницы (0-based). */
    private int page;

    /** Размер страницы. */
    private int size;

    /** Есть ли ещё данные для подгрузки. */
    private boolean hasMore;

    /**
     * Время последнего обновления данных в БД по выбранному фильтру;
     * {@code null}, если записей за период нет.
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastSyncedAt;
}
