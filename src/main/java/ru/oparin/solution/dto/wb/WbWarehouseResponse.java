package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO для ответа со списком складов WB от /api/v3/offices.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WbWarehouseResponse {

    @JsonProperty("id")
    private Integer id;

    @JsonProperty("address")
    private String address;

    @JsonProperty("name")
    private String name;

    @JsonProperty("city")
    private String city;

    @JsonProperty("longitude")
    private BigDecimal longitude;

    @JsonProperty("latitude")
    private BigDecimal latitude;

    @JsonProperty("cargoType")
    private Integer cargoType;

    @JsonProperty("deliveryType")
    private Integer deliveryType;

    @JsonProperty("federalDistrict")
    private String federalDistrict;

    @JsonProperty("selected")
    private Boolean selected;
}

