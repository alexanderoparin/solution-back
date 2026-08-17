package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

/**
 * Запрос остатков FBS: POST /api/v3/stocks/{warehouseId}.
 * Не более 1000 {@code chrtId} за запрос.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbFbsStocksRequest {

    /**
     * ID размеров товаров (chrtId).
     */
    @JsonProperty("chrtIds")
    private List<Long> chrtIds;
}
