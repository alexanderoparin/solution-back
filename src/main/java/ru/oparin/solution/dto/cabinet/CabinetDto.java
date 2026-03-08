package ru.oparin.solution.dto.cabinet;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO кабинета продавца (ответ API).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CabinetDto {

    private Long id;
    private String name;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Дата последнего успешного обновления данных по кабинету. Всегда в ответе (для блокировки кнопки по кабинету). */
    private LocalDateTime lastDataUpdateAt;
    /** Время запроса обновления (кнопка нажата). Всегда в ответе. */
    private LocalDateTime lastDataUpdateRequestedAt;

    /**
     * Информация о WB API ключе кабинета.
     */
    private ApiKeyInfo apiKey;

    /**
     * Статусы доступа к категориям WB API по кабинету (успех/неуспех последнего блока обновлений).
     */
    private List<ScopeStatusDto> scopeStatuses;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiKeyInfo {
        private String apiKey;
        private Boolean isValid;
        private LocalDateTime lastValidatedAt;
        private String validationError;
        private LocalDateTime lastDataUpdateAt;
        /** Время запроса обновления (кнопка нажата, задача в очереди). Для блокировки кнопки до старта. */
        private LocalDateTime lastDataUpdateRequestedAt;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScopeStatusDto {
        private String category;
        private String categoryDisplayName;
        private LocalDateTime lastCheckedAt;
        private Boolean success;
        private String errorMessage;
    }
}
