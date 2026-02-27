package ru.oparin.solution.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * DTO для ответа о статусе доступа пользователя к продукту.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessStatusResponse {

    private boolean hasAccess;

    private boolean agencyClient;

    private String subscriptionStatus;

    private LocalDateTime subscriptionExpiresAt;
}

