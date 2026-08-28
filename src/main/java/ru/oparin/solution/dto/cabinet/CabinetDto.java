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

    /** Тип подписки Ozon Seller для UI (с учётом override и Premium Plus probe). */
    private String ozonSubscriptionType;
    /** Русское название тарифа Ozon для UI. */
    private String ozonSubscriptionTypeDisplayName;
    /** Автоопределённый тариф из seller/info (консервативно). */
    private String ozonSubscriptionTypeDetected;
    /** Ручная настройка тарифа администратором; null — авто. */
    private String ozonSubscriptionTypeOverride;
    /** true — отображаемый тариф задан вручную. */
    private Boolean ozonSubscriptionManual;
    /** Флаг is_premium из seller/info. */
    private Boolean ozonSubscriptionIsPremium;
    /** Доступна ли воронка analytics/data (probe). */
    private Boolean ozonAnalyticsFunnelAvailable;
    /** Когда последний раз проверяли подписку Ozon. */
    private LocalDateTime ozonSubscriptionCheckedAt;

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
        /** Ozon Performance API client_secret (маскируется при maskApiKey). */
        private String ozonPerformanceClientSecret;
        /** Задан ли client_secret. */
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
