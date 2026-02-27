package ru.oparin.solution.dto.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Чек для фискализации Робокассы.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptDto {

    @JsonProperty("sno")
    @Builder.Default
    private String sno = "usn_income";

    @JsonProperty("items")
    private List<ReceiptItemDto> items;
}
