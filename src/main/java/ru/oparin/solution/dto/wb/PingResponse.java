package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO для ответа проверки подключения к WB API.
 * 
 * Документация: https://dev.wildberries.ru/openapi/api-information#tag/Proverka-podklyucheniya-k-WB-API
 * 
 * Структура ответа:
 * {
 *   "TS": "2024-08-16T11:19:05+03:00",
 *   "Status": "OK"
 * }
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PingResponse {
    
    /**
     * Временная метка ответа от сервера.
     */
    @JsonProperty("TS")
    private String ts;
    
    /**
     * Статус подключения ("OK" при успехе).
     */
    @JsonProperty("Status")
    private String status;
}



