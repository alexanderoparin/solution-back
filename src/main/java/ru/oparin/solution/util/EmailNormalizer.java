package ru.oparin.solution.util;

import java.util.Locale;

/**
 * Нормализация email: сравнение и хранение без учёта регистра.
 */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    /**
     * Приводит email к каноническому виду (trim + lowercase).
     *
     * @param email исходный email
     * @return нормализованный email или {@code null}, если вход {@code null}
     */
    public static String normalize(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Сравнивает два email без учёта регистра и пробелов по краям.
     */
    public static boolean equalsIgnoreCase(String left, String right) {
        if (left == null || right == null) {
            return left == right;
        }
        return normalize(left).equals(normalize(right));
    }
}
