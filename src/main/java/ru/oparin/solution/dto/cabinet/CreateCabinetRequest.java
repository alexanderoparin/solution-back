package ru.oparin.solution.dto.cabinet;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import ru.oparin.solution.model.CabinetTokenType;
import ru.oparin.solution.model.MarketplaceType;

/**
 * Запрос на создание кабинета (WB или Ozon).
 */
@Getter
@Setter
public class CreateCabinetRequest {

    /**
     * Маркетплейс. Если null — WB (обратная совместимость).
     */
    private MarketplaceType marketplaceType;

    @Size(min = 1, max = 255)
    private String name;

    /**
     * WB API-токен или Ozon Seller Api-Key.
     */
    @Size(min = 1, max = 500)
    private String apiKey;

    /**
     * Тип WB API токена (обязателен для WB).
     */
    private CabinetTokenType tokenType;

    /**
     * Ozon Seller Client-Id (обязателен для OZON).
     */
    @Size(min = 1, max = 64)
    private String ozonClientId;
}
