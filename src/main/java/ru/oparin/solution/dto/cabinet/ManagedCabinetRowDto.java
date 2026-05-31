package ru.oparin.solution.dto.cabinet;

import lombok.*;

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
    /** Клиент агентства (флаг селлера-владельца кабинета). */
    private Boolean sellerAgencyClient;
    /** Email владельца селлера ({@code users.owner_id}), если задан. */
    private String sellerOwnerEmail;
    private CabinetDto cabinet;
}
