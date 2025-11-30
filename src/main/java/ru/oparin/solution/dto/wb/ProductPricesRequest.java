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
 * DTO для запроса цен товаров через /api/v2/list/goods/filter.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductPricesRequest {

    @NotEmpty(message = "nmList не может быть пустым")
    @Size(min = 1, max = 1000, message = "nmList должен содержать от 1 до 1000 элементов")
    @JsonProperty("nmList")
    private List<Long> nmList;
}

