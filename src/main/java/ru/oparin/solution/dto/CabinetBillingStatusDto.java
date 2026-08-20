package ru.oparin.solution.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Состояние тарифов и услуг выбранного кабинета.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CabinetBillingStatusDto {

    private Long cabinetId;
    private MainTariffDto mainTariff;
    private List<ServiceStatusDto> services;
    private WbAbTestQuotaDto abTestQuota;
    private Boolean canManageBilling;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MainTariffDto {
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
    public static class ServiceStatusDto {
        private String serviceCode;
        private String name;
        private Boolean connected;
        private String planCode;
        private String planName;
        private LocalDateTime expiresAt;
        private String status;
    }
}
