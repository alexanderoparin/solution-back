package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO для ответа со складом продавца от /api/v3/warehouses.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SellerWarehouseResponse {
    @JsonProperty("name")
    private String name;

    @JsonProperty("officeId")
    private Integer officeId;

    @JsonProperty("id")
    private Long id;

    @JsonProperty("cargoType")
    private Integer cargoType;

    @JsonProperty("deliveryType")
    private Integer deliveryType;

    @JsonProperty("isDeleting")
    private Boolean isDeleting;

    @JsonProperty("isProcessing")
    private Boolean isProcessing;
}

