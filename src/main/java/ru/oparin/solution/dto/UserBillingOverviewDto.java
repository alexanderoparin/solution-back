package ru.oparin.solution.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Сводка биллинга пользователя для страницы «Подписка и тарифы».
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBillingOverviewDto {

    /** Активные пакеты / агрегаты подписок. */
    private List<ActivePackageDto> activePackages;

    /** Кабинеты владельца с флагами функций. */
    private List<CabinetBillingRowDto> cabinets;

    /** Квота А/Б на пользователя. */
    private WbAbTestQuotaDto abTestQuota;

    /** Можно ли управлять оплатой (владелец, почта подтверждена). */
    private boolean canManageBilling;

    /**
     * Активный пакет (PRO/РК по платежу или агрегат; А/Б — user quota).
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivePackageDto {
        private String kind;
        private String title;
        private Integer cabinetCount;
        private LocalDateTime expiresAt;
        private String status;
        /** Для А/Б: осталось / использовано. */
        private Integer remaining;
        private Integer usedStarts;

        /** Платёж, объединяющий кабинеты пакета (null у «сирот»). */
        private Long paymentId;
        /** Id плана текущего пакета. */
        private Long planId;
        private String planCode;
        private Integer minCabinets;
        private Integer maxCabinets;
        /** Кабинеты в пакете. */
        private List<Long> cabinetIds;
        /** Можно добрать кабинеты до max без оплаты. */
        private boolean canAddCabinets;
        /** Есть более высокий тир с тем же сроком. */
        private boolean canUpgrade;
        /** CAMPAIGN → парный PRO (тот же max/срок). */
        private boolean canUpgradeToPro;
    }

    /**
     * Строка кабинета в таблице подписок.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CabinetBillingRowDto {
        private Long cabinetId;
        private String name;
        private String marketplaceType;
        private LocalDateTime createdAt;
        /** FREE | PRO | CAMPAIGN | MIXED */
        private String tariffLabel;
        private String status;
        private LocalDateTime expiresAt;
        private boolean products;
        private boolean summary;
        private boolean campaigns;
        private boolean hypotheses;
        private boolean campaignManage;
        private boolean abTests;
        private boolean unlimitedAccess;
        private String mainPlanCode;
        private String campaignPlanCode;
    }
}
