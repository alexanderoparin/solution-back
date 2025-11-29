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
 * DTO для ответа получения списка кампаний по типам и статусам.
 * Эндпоинт: GET /adv/v1/promotion/count
 * Документация: https://dev.wildberries.ru/openapi/promotion#tag/Kampanii/paths/~1adv~1v1~1promotion~1count/get
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PromotionCountResponse {

    /**
     * Список кампаний, сгруппированных по типу и статусу.
     */
    @JsonProperty("adverts")
    private List<AdvertGroup> adverts;

    /**
     * Общее количество кампаний.
     */
    @JsonProperty("all")
    private Integer all;

    /**
     * Группа кампаний с одинаковым типом и статусом.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AdvertGroup {
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
         * Количество кампаний в группе.
         */
        @JsonProperty("count")
        private Integer count;

        /**
         * Список кампаний в группе.
         */
        @JsonProperty("advert_list")
        private List<AdvertInfo> advertList;
    }

    /**
     * Информация о кампании в списке.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AdvertInfo {
        /**
         * ID кампании.
         */
        @JsonProperty("advertId")
        private Long advertId;

        /**
         * Дата последнего изменения.
         */
        @JsonProperty("changeTime")
        private String changeTime;
    }
}

