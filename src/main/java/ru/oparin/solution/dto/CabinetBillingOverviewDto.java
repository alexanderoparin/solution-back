package ru.oparin.solution.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Сводная строка биллинга кабинета для админ-таблицы.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CabinetBillingOverviewDto {

    private Long cabinetId;
    private String cabinetName;
    private Long sellerId;
    private String sellerEmail;
    private Boolean agencyManaged;

    private MainTariffOverviewDto mainTariff;
    private CampaignOverviewDto campaign;
    private WbAbTestsOverviewDto abTests;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MainTariffOverviewDto {
        private String code;
        private String name;
        private String status;
        private LocalDateTime expiresAt;
        private Boolean unlimitedAccess;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CampaignOverviewDto {
        private Boolean connected;
        private String planCode;
        private String planName;
        private LocalDateTime expiresAt;
        private String status;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WbAbTestsOverviewDto {
        private Boolean connected;
        private Boolean activated;
        private Integer remaining;
        private Boolean unlimited;
    }
}
