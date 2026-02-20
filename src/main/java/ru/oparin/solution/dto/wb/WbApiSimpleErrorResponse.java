package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

/**
 * Тело ответа WB API при ошибках 400 Bad Request, 403 Forbidden и др.
 * в формате application/json (data, error, errorText, additionalErrors).
 *
 * @see <a href="https://dev.wildberries.ru/api/swagger/yaml/ru/02-products.yaml">02-products.yaml, responseBodyContentError400 / responseBodyContentError403</a>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WbApiSimpleErrorResponse {

    /** Данные ошибки (часто null). */
    private Object data;

    /** Флаг ошибки. */
    private Boolean error;

    /** Текст ошибки (например, "Access denied", "Invalid request parameters"). */
    private String errorText;

    /** Дополнительные ошибки (может быть строка или объект в JSON). */
    private Object additionalErrors;
}
