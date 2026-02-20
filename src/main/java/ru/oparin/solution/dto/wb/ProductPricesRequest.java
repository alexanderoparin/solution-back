package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

/**
 * DTO для запроса цен товаров через POST /api/v2/list/goods/filter.
 * Используется для получения информации о ценах и скидках товаров по их артикулам.
 *
 * @see <a href="https://dev.wildberries.ru/swagger/products">Swagger — WB API (Товары)</a>
 * @see <a href="https://dev.wildberries.ru/docs/openapi/work-with-products">Документация: работа с товарами</a>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductPricesRequest {
    /**
     * Список артикулов WB (nmID) для получения цен.
     * Должен содержать от 1 до 1000 элементов.
     */
    @NotEmpty(message = "nmList не может быть пустым")
    @Size(min = 1, max = 1000, message = "nmList должен содержать от 1 до 1000 элементов")
    @JsonProperty("nmList")
    private List<Long> nmList;
}

