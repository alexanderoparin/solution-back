package ru.oparin.solution.dto.cabinet;

import lombok.*;
import ru.oparin.solution.model.CabinetTokenType;
import ru.oparin.solution.model.MarketplaceType;

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
    /** Маркетплейс кабинета (WB | OZON). */
    private MarketplaceType marketplaceType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Дата последнего успешного обновления данных по кабинету. Всегда в ответе (для блокировки кнопки по кабинету). */
    private LocalDateTime lastDataUpdateAt;
    /** Время запроса обновления (кнопка нажата). Всегда в ответе. */
    private LocalDateTime lastDataUpdateRequestedAt;
    /** Время последнего успешного завершения обновления остатков. */
    private LocalDateTime lastStocksUpdateAt;

    /** Время последней успешной синхронизации списка РК Ozon. */
    private LocalDateTime lastOzonCampaignsSyncAt;

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
        private CabinetTokenType tokenType;
        /** Ozon Seller Client-Id (только для OZON). */
        private String ozonClientId;
        private Boolean isValid;
        private LocalDateTime lastValidatedAt;
        private String validationError;
        private LocalDateTime lastDataUpdateAt;
        /** Время запроса обновления (кнопка нажата, задача в очереди). Для блокировки кнопки до старта. */
        private LocalDateTime lastDataUpdateRequestedAt;
        /** Время последнего успешного завершения обновления остатков. */
        private LocalDateTime lastStocksUpdateAt;
        /** Ozon Performance API client_id. */
        private String ozonPerformanceClientId;
        /** Задан ли client_secret (сам secret не отдаётся). */
        private Boolean ozonPerformanceConfigured;
        /** Результат проверки Performance credentials. */
        private Boolean ozonPerformanceIsValid;
        private LocalDateTime ozonPerformanceLastValidatedAt;
        private String ozonPerformanceValidationError;
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
        /** До этого времени запись по категории недоступна (read-only токен WB). */
        private LocalDateTime writeBlockedUntil;
        /** Токен только для чтения по операциям записи (например start/pause РК). */
        private Boolean writeReadOnly;
    }
}
