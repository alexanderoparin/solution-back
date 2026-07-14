package ru.oparin.solution.model;

/**
 * Причина удаления аккаунта (из формы пользователя).
 */
public enum AccountDeletionReason {
    NOT_USING("Больше не пользуюсь сервисом"),
    OTHER_SERVICE("Перехожу на другой сервис"),
    FUNCTIONALITY("Не хватает функционала"),
    TOO_EXPENSIVE("Слишком дорого"),
    OTHER("Другое");

    private final String label;

    AccountDeletionReason(String label) {
        this.label = label;
    }

    /**
     * Человекочитаемая подпись причины.
     */
    public String getLabel() {
        return label;
    }
}
