package ru.oparin.solution.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO платёжной записи для отображения в ЛК.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDto {

    private Long id;
    private BigDecimal amount;
    private String currency;
    private String status;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}
