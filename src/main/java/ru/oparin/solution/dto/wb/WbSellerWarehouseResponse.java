package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Склад продавца из GET /api/v3/warehouses (Marketplace API).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WbSellerWarehouseResponse {

    /**
     * ID склада продавца.
     */
    @JsonProperty("id")
    private Long id;

    /**
     * Название склада продавца.
     */
    @JsonProperty("name")
    private String name;

    /**
     * ID офиса WB для привязки склада.
     */
    @JsonProperty("officeId")
    private Long officeId;

    /**
     * Тип товара: 1 — МГТ, 2 — СГТ, 3 — КГТ+.
     */
    @JsonProperty("cargoType")
    private Integer cargoType;

    /**
     * Тип доставки: 1 — FBS, 2 — DBS и т.д.
     */
    @JsonProperty("deliveryType")
    private Integer deliveryType;

    /**
     * Склад удаляется на стороне WB.
     */
    @JsonProperty("isDeleting")
    private Boolean isDeleting;

    /**
     * Данные склада обновляются на стороне WB.
     */
    @JsonProperty("isProcessing")
    private Boolean isProcessing;
}
