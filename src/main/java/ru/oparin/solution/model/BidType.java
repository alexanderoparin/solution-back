package ru.oparin.solution.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Типы ставок рекламных кампаний Wildberries.
 */
@Getter
@RequiredArgsConstructor
public enum BidType {
    /**
     * Ручная ставка (тип 1).
     * Управление ставками выполняется вручную.
     */
    MANUAL(1, "Ручная ставка"),

    /**
     * Единая ставка (тип 2).
     * Автоматическое управление ставками.
     */
    UNIFIED(2, "Единая ставка");

    private final Integer code;
    private final String description;

    /**
     * Преобразует числовой код в enum.
     *
     * @param code числовой код типа ставки
     * @return соответствующий enum или null, если код не найден
     */
    public static BidType fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (BidType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Проверяет, является ли код типа ставки валидным.
     *
     * @param code числовой код типа ставки
     * @return true, если код валиден
     */
    public static boolean isValid(Integer code) {
        return fromCode(code) != null;
    }
}

