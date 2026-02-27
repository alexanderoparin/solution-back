package ru.oparin.solution.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiatePaymentResponse {

    private String paymentUrl;
    private Long paymentId;
}
