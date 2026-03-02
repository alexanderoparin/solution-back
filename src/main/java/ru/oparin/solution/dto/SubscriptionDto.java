package ru.oparin.solution.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * DTO подписки для админки (список по пользователю).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionDto {

    private Long id;
    private Long userId;
    private Long planId;
    private String planName;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
