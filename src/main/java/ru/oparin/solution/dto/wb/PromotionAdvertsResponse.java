package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * DTO для ответа получения детальной информации о кампаниях.
 * Эндпоинт: POST /adv/v1/promotion/adverts
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PromotionAdvertsResponse {

    /**
     * Список кампаний.
     */
    @JsonProperty("adverts")
    private List<Campaign> adverts;

    /**
     * Информация о кампании.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Campaign {
        /**
         * ID кампании.
         */
        @JsonProperty("advertId")
        private Long advertId;

        /**
         * Название кампании.
         */
        @JsonProperty("name")
        private String name;

        /**
         * Тип кампании.
         */
        @JsonProperty("type")
        private Integer type;

        /**
         * Статус кампании.
         */
        @JsonProperty("status")
        private Integer status;

        /**
         * Тип ставки: 1 - "manual" (ручная), 2 - "unified" (единая).
         */
        @JsonProperty("bid_type")
        private Integer bidType;

        /**
         * Дата начала кампании.
         */
        @JsonProperty("startTime")
        private String startTime;

        /**
         * Дата окончания кампании.
         */
        @JsonProperty("endTime")
        private String endTime;

        /**
         * Дата создания кампании.
         */
        @JsonProperty("createTime")
        private String createTime;

        /**
         * Дата последнего изменения.
         */
        @JsonProperty("changeTime")
        private String changeTime;
    }
}

