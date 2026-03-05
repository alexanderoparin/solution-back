package ru.oparin.solution.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Типы рекламных кампаний Wildberries.
 * 
 * В системе обрабатываются только актуальные типы кампаний:
 * - Тип 8: Единая ставка (в API WB — bid_type "unified")
 * - Тип 9: Ручная ставка (в API WB — bid_type "manual")
 * 
 * Детальная информация по кампаниям получается через единый эндпоинт GET /api/advert/v2/adverts.
 * Типы 4, 5, 6, 7 устарели и не обрабатываются системой.
 */
@Getter
@RequiredArgsConstructor
public enum CampaignType {
    /**
     * Устаревший тип кампании (тип 4).
     * Не используется в системе.
     */
    TYPE_4(4, "Устаревший тип кампании"),

    /**
     * Устаревший тип кампании (тип 5).
     * Не используется в системе.
     */
    TYPE_5(5, "Устаревший тип кампании"),

    /**
     * Устаревший тип кампании (тип 6).
     * Не используется в системе.
     */
    TYPE_6(6, "Устаревший тип кампании"),

    /**
     * Устаревший тип кампании (тип 7).
     * Не используется в системе.
     */
    TYPE_7(7, "Устаревший тип кампании"),

    /**
     * Единая ставка (тип 8, в API WB — bid_type "unified").
     * Актуальный тип кампании. Детали загружаются через GET /api/advert/v2/adverts.
     */
    AUTOMATIC(8, "Единая ставка"),

    /**
     * Ручная ставка (тип 9, в API WB — bid_type "manual").
     * Актуальный тип кампании. Детали загружаются через GET /api/advert/v2/adverts.
     */
    AUCTION(9, "Ручная ставка");

    private final Integer code;
    private final String description;

    /**
     * Преобразует числовой код в enum.
     *
     * @param code числовой код типа кампании
     * @return соответствующий enum или null, если код не найден
     */
    public static CampaignType fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (CampaignType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Проверяет, является ли тип кампании валидным.
     *
     * @param code числовой код типа кампании
     * @return true, если код валиден
     */
    public static boolean isValid(Integer code) {
        return fromCode(code) != null;
    }
}

