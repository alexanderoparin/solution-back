package ru.oparin.solution.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Типы рекламных кампаний Wildberries.
 * 
 * В системе обрабатываются только актуальные типы кампаний:
 * - Тип 8: Автоматическая РК (получается через /adv/v1/promotion/adverts)
 * - Тип 9: Аукцион (получается через /adv/v0/auction/adverts)
 * 
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
     * Автоматическая РК (тип 8).
     * Актуальный тип кампании. Детальная информация получается через эндпоинт /adv/v1/promotion/adverts.
     * Содержит информацию об артикулах, участвующих в кампании.
     */
    AUTOMATIC(8, "Автоматическая РК"),

    /**
     * Аукцион (тип 9).
     * Актуальный тип кампании. Детальная информация получается через эндпоинт /adv/v0/auction/adverts.
     * Содержит информацию об артикулах, участвующих в кампании.
     */
    AUCTION(9, "Аукцион");

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

