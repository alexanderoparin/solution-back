package ru.oparin.solution.service.wb;

import java.util.Optional;

/**
 * Контекст попытки WB API-события для текущего потока.
 * Устанавливается {@link ru.oparin.solution.service.events.WbApiEventDispatcher} на время выполнения события,
 * чтобы HTTP-клиенты логировали {@code attempt_count} / {@code max_attempts} из очереди, а не локальный счётчик.
 */
public final class WbApiEventAttemptContext {

    private static final ThreadLocal<AttemptInfo> HOLDER = new ThreadLocal<>();

    private WbApiEventAttemptContext() {
    }

    /**
     * @param attemptCount уже учтённые неуспешные попытки события (поле {@code attempt_count} до текущего запуска)
     * @param maxAttempts  лимит попыток события
     * @param eventId      id события (для отладки)
     */
    public static void set(int attemptCount, int maxAttempts, Long eventId) {
        HOLDER.set(new AttemptInfo(attemptCount, maxAttempts, eventId));
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static Optional<AttemptInfo> current() {
        return Optional.ofNullable(HOLDER.get());
    }

    /**
     * Номер попытки для лога: из контекста события или локальный (внутри одного вызова клиента).
     *
     * @param localAttemptOneBased попытка 1..localMax внутри одного вызова клиента
     * @param localMax             локальный лимит (например {@link AbstractWbApiClient#MAX_CONNECTION_RETRIES})
     */
    public static AttemptDisplay resolveAttemptDisplay(int localAttemptOneBased, int localMax) {
        return current()
                .map(info -> new AttemptDisplay(info.currentAttemptNumber(), info.maxAttempts()))
                .orElse(new AttemptDisplay(localAttemptOneBased, localMax));
    }

    public record AttemptInfo(int attemptCount, int maxAttempts, Long eventId) {
        /**
         * Номер текущей попытки события (1-based): первая обработка — 1, после одного defer — 2 и т.д.
         */
        public int currentAttemptNumber() {
            return attemptCount + 1;
        }
    }

    public record AttemptDisplay(int attempt, int maxAttempts) {
    }
}
