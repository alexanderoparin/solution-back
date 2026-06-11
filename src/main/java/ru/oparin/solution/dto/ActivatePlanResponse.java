package ru.oparin.solution.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivatePlanResponse {

    private Long subscriptionId;

    private LocalDateTime expiresAt;
}
