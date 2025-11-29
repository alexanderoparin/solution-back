package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * DTO для запроса детальной информации о кампаниях.
 * Эндпоинт: POST /adv/v1/promotion/adverts
 * Документация: https://dev.wildberries.ru/openapi/promotion#tag/Kampanii/paths/~1adv~1v1~1promotion~1adverts/post
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionAdvertsRequest {

    /**
     * Список ID кампаний для получения информации.
     */
    @NotEmpty(message = "Список ID кампаний не может быть пустым")
    @JsonProperty("id")
    private List<Long> id;
}

