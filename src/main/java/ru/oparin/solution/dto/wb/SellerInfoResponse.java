package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO для ответа информации о продавце WB API.
 * 
 * Документация: https://dev.wildberries.ru/openapi/api-information#tag/Informaciya-o-prodavce
 * 
 * Структура ответа:
 * {
 *   "name": "ИП Кружинин В. Р.",
 *   "sid": "e8923014-e233-47q8-898e-3cc86d67ea61",
 *   "tradeMark": "Flax Store"
 * }
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SellerInfoResponse {
    
    /**
     * Наименование продавца.
     */
    private String name;
    
    /**
     * ID аккаунта продавца.
     */
    private String sid;
    
    /**
     * Торговая марка продавца.
     */
    private String tradeMark;
}



