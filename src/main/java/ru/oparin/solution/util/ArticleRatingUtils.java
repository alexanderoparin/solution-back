package ru.oparin.solution.util;

import java.math.BigDecimal;

/**
 * Нормализация рейтинга по отзывам WB (шкала 1–5, источник item-rating).
 */
public final class ArticleRatingUtils {

    private ArticleRatingUtils() {
    }

    /**
     * Рейтинг пригоден для отображения и хранения (не null и больше нуля).
     */
    public static boolean isMeaningful(BigDecimal rating) {
        return rating != null && rating.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Значение для API/UI: null вместо нуля и отрицательных.
     */
    public static BigDecimal toDisplayRating(BigDecimal rating) {
        return isMeaningful(rating) ? rating : null;
    }

    /**
     * Обновление рейтинга после синка: WB может прислать 0 — не затираем ранее известный ненулевой рейтинг.
     */
    public static BigDecimal resolveRatingAfterSync(BigDecimal existing, BigDecimal fromWb) {
        if (isMeaningful(fromWb)) {
            return fromWb;
        }
        if (isMeaningful(existing)) {
            return existing;
        }
        return null;
    }
}
