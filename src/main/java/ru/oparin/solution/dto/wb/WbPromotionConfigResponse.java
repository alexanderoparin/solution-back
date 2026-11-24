package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

/**
 * Ответ GET /api/advert/v1/config — конфигурация продвижения профиля продавца.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WbPromotionConfigResponse {

    /**
     * Валюта профиля продавца (ISO 4217).
     */
    private String currency;

    /**
     * Числовой код валюты.
     */
    private Integer currencyCode;

    /**
     * Шаг ставки CPM в разменных единицах (0,01 базовой валюты).
     */
    private Long cpmStep;

    /**
     * Шаг ставки CPC в разменных единицах (0,01 базовой валюты).
     */
    private Long cpcStep;

    /**
     * Минимальная сумма пополнения бюджета в разменных единицах.
     */
    private Long minTopUp;

    /**
     * Минимальный дневной лимит в разменных единицах.
     */
    private Long minDailyLimit;
}
