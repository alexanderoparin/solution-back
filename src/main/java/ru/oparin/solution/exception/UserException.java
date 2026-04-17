package ru.oparin.solution.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Исключение при работе с пользователями.
 */
@Getter
public class UserException extends RuntimeException {

    private final HttpStatus httpStatus;
    /**
     * Подсказка клиенту: через сколько секунд можно повторить запрос (например, X-Ratelimit-Retry при 429).
     */
    private final Integer retryAfterSeconds;

    public UserException(String message) {
        this(message, HttpStatus.BAD_REQUEST, null, null);
    }

    public UserException(String message, HttpStatus httpStatus) {
        this(message, httpStatus, null, null);
    }

    public UserException(String message, HttpStatus httpStatus, Integer retryAfterSeconds) {
        this(message, httpStatus, retryAfterSeconds, null);
    }

    public UserException(String message, HttpStatus httpStatus, Throwable cause) {
        this(message, httpStatus, null, cause);
    }

    private UserException(String message, HttpStatus httpStatus, Integer retryAfterSeconds, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.retryAfterSeconds = retryAfterSeconds;
    }
}

