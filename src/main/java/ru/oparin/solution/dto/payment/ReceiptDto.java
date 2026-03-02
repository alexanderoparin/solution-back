package ru.oparin.solution.dto.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Чек для фискализации Робокассы. Порядок полей фиксирован для одинаковой подписи и URL. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({"sno", "items"})
public class ReceiptDto {

    @JsonProperty("sno")
    @Builder.Default
    private String sno = "usn_income";

    @JsonProperty("items")
    private List<ReceiptItemDto> items;
}
