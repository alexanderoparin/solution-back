package ru.oparin.solution.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Статусы рекламных кампаний Wildberries.
 * 
 * Статусы используются для управления жизненным циклом кампаний:
 * - Кампания создается и настраивается
 * - Переходит в статус "Готово к запуску" (4)
 * - Запускается и становится "Активна" (9)
 * - Может быть поставлена на "Паузу" (11)
 * - После окончания становится "Завершена" (7)
 */
@Getter
@RequiredArgsConstructor
public enum CampaignStatus {
    /**
     * Готово к запуску (статус 4).
     * Кампания подготовлена и ожидает запуска.
     * Для активации необходимо выполнить условия: применить изменения и установить бюджет.
     */
    READY_TO_START(4, "Готово к запуску"),

    /**
     * Завершена (статус 7).
     * Кампания завершила свою работу и больше не активна.
     */
    FINISHED(7, "Завершена"),

    /**
     * Активна (статус 9).
     * Кампания в данный момент запущена и работает.
     */
    ACTIVE(9, "Активна"),

    /**
     * Пауза (статус 11).
     * Кампания временно приостановлена. Её можно возобновить при необходимости.
     */
    PAUSED(11, "Пауза");

    private final Integer code;
    private final String description;

    /**
     * Преобразует числовой код в enum.
     *
     * @param code числовой код статуса кампании
     * @return соответствующий enum или null, если код не найден
     */
    public static CampaignStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (CampaignStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * Проверяет, является ли статус кампании валидным.
     *
     * @param code числовой код статуса кампании
     * @return true, если код валиден
     */
    public static boolean isValid(Integer code) {
        return fromCode(code) != null;
    }
}

