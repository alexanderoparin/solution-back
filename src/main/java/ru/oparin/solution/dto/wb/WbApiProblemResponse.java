package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Тело ответа WB API при ошибках 4xx (401 Unauthorized, 429 Too Many Requests и др.)
 * в формате application/problem+json.
 *
 * @see <a href="https://dev.wildberries.ru/api/swagger/yaml/ru/02-products.yaml">02-products.yaml, responses.401</a>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WbApiProblemResponse {

    /** Заголовок ошибки (например, "unauthorized"). */
    private String title;

    /** Детали ошибки (например, описание проблемы с токеном). */
    private String detail;

    /** Внутренний код ошибки WB. */
    private String code;

    /** Уникальный ID запроса. */
    @JsonProperty("requestId")
    private String requestId;

    /** ID внутреннего сервиса WB (например, "s2s-api-auth-catalog"). */
    private String origin;

    /** HTTP статус-код. */
    private Integer status;

    /** Расшифровка HTTP статус-кода (например, "Unauthorized"). */
    private String statusText;

    /** Дата и время запроса (ISO-8601). */
    private String timestamp;
}
