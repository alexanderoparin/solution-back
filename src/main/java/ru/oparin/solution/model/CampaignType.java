package ru.oparin.solution.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Типы рекламных кампаний Wildberries.
 * 
 * С 23 октября 2025 года все новые кампании создаются с типом 9.
 * Типы 4, 5, 6, 7, 8 устарели, но могут встречаться в старых данных.
 */
@Getter
@RequiredArgsConstructor
public enum CampaignType {
    /**
     * Устаревший тип кампании (тип 4).
     */
    TYPE_4(4, "Устаревший тип кампании"),

    /**
     * Устаревший тип кампании (тип 5).
     */
    TYPE_5(5, "Устаревший тип кампании"),

    /**
     * Устаревший тип кампании (тип 6).
     */
    TYPE_6(6, "Устаревший тип кампании"),

    /**
     * Устаревший тип кампании (тип 7).
     */
    TYPE_7(7, "Устаревший тип кампании"),

    /**
     * Устаревший тип кампании (тип 8).
     */
    TYPE_8(8, "Устаревший тип кампании"),

    /**
     * Унифицированный тип кампании (тип 9).
     * Все новые кампании создаются с этим типом.
     * Стратегия управления ставками определяется полем bidType (manual/unified).
     */
    UNIFIED(9, "Унифицированный тип кампании");

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

