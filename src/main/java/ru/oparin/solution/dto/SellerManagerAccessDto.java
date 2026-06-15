package ru.oparin.solution.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * Менеджер с делегированным доступом к аккаунту селлера.
 */
@Value
@Builder
public class SellerManagerAccessDto {
    Long managerId;
    String managerEmail;
    LocalDateTime grantedAt;
}
