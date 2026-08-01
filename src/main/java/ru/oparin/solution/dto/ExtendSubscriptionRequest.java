package ru.oparin.solution.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Запрос ручного назначения/продления подписки или начисления А/Б-квоты (ADMIN).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtendSubscriptionRequest {

    /** Владелец (для аудита / совместимости). */
    private Long userId;

    @NotNull
    private Long cabinetId;

    @NotNull
    private Long planId;

    /** Если не указано — считается от текущего момента + period плана (для MAIN/CAMPAIGN). */
    private LocalDateTime expiresAt;

    /** Для AB_PACK: сколько кредитов начислить (если null — creditAmount плана). */
    private Integer abCredits;
}
