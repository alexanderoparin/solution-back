package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ответ GET /api/v1/feedbacks.
 * Документация: https://dev.wildberries.ru/docs/openapi/user-communication#tag/Otzyvy
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FeedbacksResponse {
    private FeedbacksData data;
    private Boolean error;
    private String errorText;
}
