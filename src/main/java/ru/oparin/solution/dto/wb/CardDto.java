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
 * DTO для карточки товара из WB API.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CardDto {

    @JsonProperty("nmID")
    private Long nmId;

    @JsonProperty("imtID")
    private Long imtId;

    @JsonProperty("nmUUID")
    private String nmUuid;

    @JsonProperty("subjectID")
    private Integer subjectId;

    @JsonProperty("subjectName")
    private String subjectName;

    @JsonProperty("vendorCode")
    private String vendorCode;

    private String brand;

    private String title;

    private String description;

    @JsonProperty("needKiz")
    private Boolean needKiz;

    private List<Photo> photos;

    private String video;

    private Wholesale wholesale;

    private Dimensions dimensions;

    private List<Characteristic> characteristics;

    private List<Size> sizes;

    private List<Tag> tags;

    @JsonProperty("createdAt")
    private String createdAt;

    @JsonProperty("updatedAt")
    private String updatedAt;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Photo {
        private String big;
        
        @JsonProperty("c246x328")
        private String c246x328;
        
        @JsonProperty("c516x688")
        private String c516x688;
        
        private String hq;
        
        private String square;
        
        private String tm;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Wholesale {
        private Boolean enabled;
        private Integer quantum;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Dimensions {
        private Integer length;
        private Integer width;
        private Integer height;
        
        @JsonProperty("weightBrutto")
        private Double weightBrutto;
        
        @JsonProperty("isValid")
        private Boolean isValid;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Characteristic {
        private Integer id;
        private String name;
        private Object value;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Size {
        @JsonProperty("chrtID")
        private Long chrtId;
        
        @JsonProperty("techSize")
        private String techSize;
        
        @JsonProperty("wbSize")
        private String wbSize;
        
        private List<String> skus;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Tag {
        private Integer id;
        private String name;
        private String color;
    }
}

