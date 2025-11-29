package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.User;
import ru.oparin.solution.model.WbApiKey;

/**
 * Сервис для работы с контекстом продавца.
 */
@Service
@RequiredArgsConstructor
public class SellerContextService {

    private final UserService userService;
    private final WbApiKeyService wbApiKeyService;

    /**
     * Создает контекст продавца из данных аутентификации.
     *
     * @param authentication данные аутентификации
     * @return контекст продавца
     */
    public SellerContext createContext(Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        validateSellerRole(user);

        WbApiKey apiKey = wbApiKeyService.findByUserId(user.getId());
        return new SellerContext(user, apiKey.getApiKey());
    }

    private void validateSellerRole(User user) {
        if (user.getRole() != Role.SELLER) {
            throw new UserException("Только SELLER может выполнять эту операцию");
        }
    }

    /**
     * Контекст продавца.
     */
    public record SellerContext(User user, String apiKey) {
    }
}

