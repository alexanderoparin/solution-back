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
 * DTO для ответа получения детальной информации об аукционных кампаниях.
 * Эндпоинт: GET /adv/v0/auction/adverts
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuctionAdvertsResponse {

    /**
     * Список аукционных кампаний.
     */
    @JsonProperty("adverts")
    private List<AuctionCampaign> adverts;

    /**
     * Информация об аукционной кампании.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AuctionCampaign {
        /**
         * ID кампании.
         */
        @JsonProperty("id")
        private Long id;

        /**
         * Статус кампании: -1, 4, 7, 8, 9, 11.
         */
        @JsonProperty("status")
        private Integer status;

        /**
         * Настройки товаров (nm_settings).
         */
        @JsonProperty("nm_settings")
        private List<NmSetting> nmSettings;

        /**
         * Ставки.
         */
        @JsonProperty("bids")
        private Bids bids;

        /**
         * Предмет.
         */
        @JsonProperty("subject")
        private Subject subject;

        /**
         * Настройки кампании.
         */
        @JsonProperty("settings")
        private Settings settings;

        /**
         * Временные отметки.
         */
        @JsonProperty("timestamps")
        private Timestamps timestamps;

        /**
         * Тип ставки: unified или manual.
         */
        @JsonProperty("bid_type")
        private String bidType;
    }

    /**
     * Настройки товара (nm_settings).
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NmSetting {
        /**
         * Артикул WB (nm_id).
         */
        @JsonProperty("nm_id")
        private Long nmId;
    }

    /**
     * Ставки.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Bids {
        // Структура ставок может быть разной, пока игнорируем
        // Пустой класс, используется только для десериализации JSON
    }

    /**
     * Предмет.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Subject {
        /**
         * ID предмета.
         */
        @JsonProperty("id")
        private Long id;

        /**
         * Название предмета.
         */
        @JsonProperty("name")
        private String name;
    }

    /**
     * Настройки кампании.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Settings {
        /**
         * Тип оплаты: cpm или cpc.
         */
        @JsonProperty("payment_type")
        private String paymentType;

        /**
         * Имя кампании.
         */
        @JsonProperty("name")
        private String name;

        /**
         * Места размещения.
         */
        @JsonProperty("placements")
        private Placements placements;
    }

    /**
     * Места размещения.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Placements {
        /**
         * Размещение в поиске.
         */
        @JsonProperty("search")
        private Boolean search;

        /**
         * Размещение в рекомендациях.
         */
        @JsonProperty("recommendations")
        private Boolean recommendations;
    }

    /**
     * Временные отметки.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Timestamps {
        /**
         * Время создания кампании.
         */
        @JsonProperty("created")
        private String created;

        /**
         * Время последнего изменения кампании.
         */
        @JsonProperty("updated")
        private String updated;

        /**
         * Время последнего запуска кампании.
         */
        @JsonProperty("started")
        private String started;

        /**
         * Время удаления кампании.
         */
        @JsonProperty("deleted")
        private String deleted;
    }
}

