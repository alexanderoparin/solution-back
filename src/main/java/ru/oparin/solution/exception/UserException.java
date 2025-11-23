package ru.oparin.solution.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Исключение при работе с пользователями.
 */
@Getter
public class UserException extends RuntimeException {

    private final HttpStatus httpStatus;

    public UserException(String message) {
        super(message);
        this.httpStatus = HttpStatus.BAD_REQUEST;
    }

    public UserException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public UserException(String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }
}

