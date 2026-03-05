package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

/**
 * DTO ответа GET /api/advert/v2/adverts — информация о кампаниях с единой или ручной ставкой.
 * Документация: <a href="https://dev.wildberries.ru/docs/openapi/promotion#tag/Kampanii/paths/~1api~1advert~1v2~1adverts/get">...</a>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AdvertsV2Response {

    @JsonProperty("adverts")
    private List<Advert> adverts;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Advert {
        @JsonProperty("bid_type")
        private String bidType;

        @JsonProperty("id")
        private Long id;

        @JsonProperty("nm_settings")
        private List<NmSetting> nmSettings;

        @JsonProperty("settings")
        private Settings settings;

        @JsonProperty("status")
        private Integer status;

        @JsonProperty("timestamps")
        private Timestamps timestamps;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NmSetting {
        @JsonProperty("bids_kopecks")
        private BidsKopecks bidsKopecks;

        @JsonProperty("nm_id")
        private Long nmId;

        @JsonProperty("subject")
        private Subject subject;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BidsKopecks {
        @JsonProperty("search")
        private Long search;

        @JsonProperty("recommendations")
        private Long recommendations;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Subject {
        @JsonProperty("id")
        private Long id;

        @JsonProperty("name")
        private String name;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Settings {
        @JsonProperty("name")
        private String name;

        @JsonProperty("payment_type")
        private String paymentType;

        @JsonProperty("placements")
        private Placements placements;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Placements {
        @JsonProperty("search")
        private Boolean search;

        @JsonProperty("recommendations")
        private Boolean recommendations;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Timestamps {
        @JsonProperty("created")
        private String created;

        @JsonProperty("updated")
        private String updated;

        @JsonProperty("started")
        private String started;

        @JsonProperty("deleted")
        private String deleted;
    }
}
