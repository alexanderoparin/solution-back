package ru.oparin.solution.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Промокод для админки.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromoCodeAdminDto {

    private Long id;
    private String code;
    private String description;
    private Integer durationDays;
    private boolean active;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private LocalDateTime createdAt;
}
