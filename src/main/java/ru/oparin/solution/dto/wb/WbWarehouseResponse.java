package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO для ответа со списком складов WB от /api/v1/warehouses.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WbWarehouseResponse {

    /**
     * ID склада.
     */
    @JsonProperty("ID")
    private Integer id;

    /**
     * Название склада.
     */
    @JsonProperty("name")
    private String name;

    /**
     * Адрес склада.
     */
    @JsonProperty("address")
    private String address;

    /**
     * Режим работы склада.
     */
    @JsonProperty("workTime")
    private String workTime;

    /**
     * Принимает ли склад QR-поставки:
     * true — да
     * false — нет
     */
    @JsonProperty("acceptsQr")
    private Boolean acceptsQr;

    /**
     * Доступен ли в качестве склада назначения:
     * true — да
     * false — нет
     */
    @JsonProperty("isActive")
    private Boolean isActive;

    /**
     * Доступен ли в качестве транзитного склада:
     * true — да
     * false — нет
     */
    @JsonProperty("isTransitActive")
    private Boolean isTransitActive;
}
