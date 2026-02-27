package ru.oparin.solution.dto.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Позиция чека для фискализации Робокассы.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptItemDto {

    @JsonProperty("name")
    private String name;

    @JsonProperty("quantity")
    private Integer quantity;

    @JsonProperty("sum")
    private BigDecimal sum;

    @JsonProperty("payment_method")
    @Builder.Default
    private String paymentMethod = "full_prepayment";

    @JsonProperty("payment_object")
    @Builder.Default
    private String paymentObject = "service";

    @JsonProperty("tax")
    @Builder.Default
    private String tax = "none";
}
