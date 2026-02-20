package ru.oparin.solution.exception;

import org.springframework.web.client.HttpClientErrorException;
import ru.oparin.solution.service.wb.WbApiCategory;

/**
 * 401 от WB API с указанием, что токен не имеет доступа к категории (token scope not allowed).
 * Позволяет в сервисах с контекстом кабинета логировать: «для кабинета X нет доступа к категории Y».
 */
public class WbApiUnauthorizedScopeException extends RuntimeException {

    private final WbApiCategory category;

    public WbApiUnauthorizedScopeException(HttpClientErrorException cause, WbApiCategory category) {
        super(cause.getMessage(), cause);
        this.category = category;
    }

    public WbApiCategory getCategory() {
        return category;
    }
}
