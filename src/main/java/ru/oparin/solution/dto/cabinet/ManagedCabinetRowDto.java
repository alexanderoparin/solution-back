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
    private CabinetDto cabinet;
}
