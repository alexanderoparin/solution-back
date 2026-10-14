package ru.oparin.solution.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Платёж для списка в админке «Тарифы и услуги».
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPaymentDto {

    private Long id;
    private LocalDateTime createdAt;
    private String userEmail;
    private Long cabinetId;
    private String cabinetName;
    private String planName;
    private BigDecimal amount;
    private String currency;
    private String status;
    private LocalDateTime paidAt;
    private String externalId;
    private String description;
}
