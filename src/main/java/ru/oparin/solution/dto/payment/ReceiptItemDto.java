package ru.oparin.solution.dto.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Позиция чека для фискализации Робокассы. Порядок полей фиксирован для одинаковой подписи и URL. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({"name", "quantity", "sum", "payment_method", "payment_object", "tax"})
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
