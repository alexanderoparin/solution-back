package ru.oparin.solution.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

/**
 * DTO для ответа с ошибкой.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    /**
     * Текст ошибки.
     */
    private String error;

    /**
     * Секунды до повтора (например, при 429 от WB API в теле ответа создания кабинета).
     */
    private Long retryAfterSeconds;
}

