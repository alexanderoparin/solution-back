package ru.oparin.solution.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * DTO для запроса остатков товаров через эндпоинт.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StocksRequest {

    @NotNull(message = "warehouseId не может быть null")
    private Long warehouseId;

    @NotEmpty(message = "nmIds не может быть пустым")
    private List<Long> nmIds;
}

