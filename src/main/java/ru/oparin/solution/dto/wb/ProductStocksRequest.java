package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * DTO для запроса остатков товаров через /api/v3/stocks/{warehouseId}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductStocksRequest {

    @NotEmpty(message = "skus не может быть пустым")
    @Size(min = 1, max = 1000, message = "skus должен содержать от 1 до 1000 элементов")
    @JsonProperty("skus")
    private List<String> skus;
}

