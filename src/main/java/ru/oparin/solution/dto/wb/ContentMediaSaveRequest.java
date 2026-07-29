package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

/**
 * Тело запроса POST /content/v3/media/save.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContentMediaSaveRequest {

    @JsonProperty("nmId")
    private Long nmId;

    /** URL медиафайлов; первый элемент становится главным фото. */
    private List<String> data;
}
