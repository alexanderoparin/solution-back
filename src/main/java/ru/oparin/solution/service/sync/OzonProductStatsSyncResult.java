package ru.oparin.solution.service.sync;

import lombok.Builder;
import lombok.Getter;

/**
 * Результат async-синхронизации product-stats Ozon Performance.
 */
@Getter
@Builder
public class OzonProductStatsSyncResult {

    public enum Status {
        /** Отчёт сохранён или кампаний нет. */
        COMPLETED,
        /** Отчёт ещё формируется — нужен повтор с тем же UUID. */
        PENDING,
        /** Ошибка отчёта — пропускаем product-stats, daily stats уже сохранены. */
        SKIPPED
    }

    private final Status status;
    private final int rowsSaved;
    private final String reportUuid;
    private final String message;

    public static OzonProductStatsSyncResult completed(int rowsSaved) {
        return OzonProductStatsSyncResult.builder()
                .status(Status.COMPLETED)
                .rowsSaved(rowsSaved)
                .build();
    }

    public static OzonProductStatsSyncResult pending(String reportUuid) {
        return OzonProductStatsSyncResult.builder()
                .status(Status.PENDING)
                .reportUuid(reportUuid)
                .build();
    }

    public static OzonProductStatsSyncResult skipped(String message) {
        return OzonProductStatsSyncResult.builder()
                .status(Status.SKIPPED)
                .message(message)
                .build();
    }
}
