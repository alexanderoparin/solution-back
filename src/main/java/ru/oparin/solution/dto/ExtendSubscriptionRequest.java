package ru.oparin.solution.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Запрос ручного назначения/продления подписки (только ADMIN).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtendSubscriptionRequest {

    @NotNull
    private Long userId;

    @NotNull
    private Long planId;

    /** Если не указано — считается от текущего момента + periodDays плана. */
    private LocalDateTime expiresAt;
}
