package ru.oparin.solution.exception;

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import ru.oparin.solution.dto.ErrorResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * Глобальный обработчик исключений.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Обработка ошибок валидации.
     *
     * @param ex исключение валидации
     * @return ответ с ошибками валидации
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = extractValidationErrors(ex);
        return ResponseEntity.badRequest().body(errors);
    }

    /**
     * Обработка UserException.
     *
     * @param ex исключение
     * @return ответ с ошибкой
     */
    @ExceptionHandler(UserException.class)
    public ResponseEntity<ErrorResponse> handleUserException(UserException ex) {
        ErrorResponse error = createErrorResponse(ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus()).body(error);
    }

    /**
     * Обработка ошибок аутентификации Spring Security.
     *
     * @param ex исключение
     * @return ответ с ошибкой
     */
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            org.springframework.security.core.AuthenticationException ex) {
        log.warn("Ошибка аутентификации: {}", ex.getMessage());
        ErrorResponse error = createErrorResponse("Неверный email или пароль");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    /**
     * Обработка BadCredentialsException (неверный пароль).
     *
     * @param ex исключение
     * @return ответ с ошибкой
     */
    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(
            org.springframework.security.authentication.BadCredentialsException ex) {
        log.warn("Неверные учетные данные: {}", ex.getMessage());
        ErrorResponse error = createErrorResponse("Неверный email или пароль");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    /**
     * Обработка IllegalArgumentException (некорректные аргументы).
     *
     * @param ex исключение
     * @return ответ с ошибкой
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Некорректный аргумент: {}", ex.getMessage(), ex);
        ErrorResponse error = createErrorResponse(ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Обработка исключений разрыва соединения клиентом.
     * Это нормальная ситуация (клиент закрыл вкладку, обновил страницу и т.д.),
     * не нужно логировать как ошибку.
     *
     * @param ex исключение
     * @return пустой ответ (соединение уже разорвано)
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<Void> handleAsyncRequestNotUsableException(AsyncRequestNotUsableException ex) {
        // Проверяем, является ли причиной разрыв соединения клиентом
        Throwable cause = ex.getCause();
        if (cause instanceof ClientAbortException ||
            (cause != null && cause.getCause() instanceof ClientAbortException)) {
            // Логируем только на уровне DEBUG, так как это нормальная ситуация
            log.debug("Клиент разорвал соединение: {}", ex.getMessage());
        } else {
            // Если причина другая, логируем как предупреждение
            log.warn("Проблема с асинхронным запросом: {}", ex.getMessage());
        }
        // Возвращаем пустой ответ, так как соединение уже разорвано
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    /**
     * Обработка общих исключений.
     *
     * @param ex исключение
     * @return ответ с ошибкой сервера
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Внутренняя ошибка сервера", ex);
        ErrorResponse error = createErrorResponse("Внутренняя ошибка сервера");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Извлекает ошибки валидации из исключения.
     */
    private Map<String, String> extractValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return errors;
    }

    /**
     * Создает ответ с ошибкой.
     */
    private ErrorResponse createErrorResponse(String message) {
        return ErrorResponse.builder()
                .error(message)
                .build();
    }
}
