package ru.oparin.solution.dto.cabinet;

import lombok.*;
import ru.oparin.solution.model.CabinetTokenType;

import java.time.LocalDateTime;

/**
 * Кабинет с API-ключом для переключения контекста в шапке (ADMIN / MANAGER).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkContextCabinetDto {

    private Long cabinetId;
    private Long sellerId;
    private String cabinetName;
    private String sellerEmail;
    private LocalDateTime lastDataUpdateAt;
    private LocalDateTime lastDataUpdateRequestedAt;
    /** Тип токена WB API кабинета (для UI: рейтинг item-rating только при PERSONAL). */
    private CabinetTokenType tokenType;
}
