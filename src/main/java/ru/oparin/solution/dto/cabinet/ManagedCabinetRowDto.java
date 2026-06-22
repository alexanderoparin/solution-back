package ru.oparin.solution.dto.cabinet;

import lombok.*;

import java.util.List;

/**
 * Строка плоского списка кабинетов для админа/менеджера: кабинет и владелец-селлер.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagedCabinetRowDto {

    private Long sellerId;
    private String sellerEmail;
    /** Клиент агентства (флаг на селлере; одинаков для всех его кабинетов). */
    private Boolean agencyManaged;
    /** Email менеджеров с активным доступом к селлеру. */
    private List<String> managerEmails;
    private CabinetDto cabinet;
}
