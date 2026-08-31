package ru.oparin.solution.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Строка таблицы активаций промокодов в админке.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromoCodeRedemptionAdminDto {

    private Long id;
    private String email;
    private String promoCode;
    private LocalDateTime redeemedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime userRegisteredAt;
}
