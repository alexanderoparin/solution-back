package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.User;
import ru.oparin.solution.model.WbApiKey;
import ru.oparin.solution.repository.UserRepository;
import ru.oparin.solution.repository.WbApiKeyRepository;

import java.util.List;

/**
 * Сервис для работы с контекстом продавца.
 */
@Service
@RequiredArgsConstructor
public class SellerContextService {

    private final UserService userService;
    private final WbApiKeyService wbApiKeyService;
    private final UserRepository userRepository;
    private final WbApiKeyRepository wbApiKeyRepository;

    /**
     * Создает контекст продавца из данных аутентификации.
     * Для SELLER использует текущего пользователя.
     * Для ADMIN/MANAGER использует выбранного sellerId (если указан) или последнего активного селлера.
     *
     * @param authentication данные аутентификации
     * @param sellerId ID селлера (опционально, только для ADMIN/MANAGER)
     * @return контекст продавца
     */
    public SellerContext createContext(Authentication authentication, Long sellerId) {
        User currentUser = userService.findByEmail(authentication.getName());
        
        User seller;
        if (currentUser.getRole() == Role.SELLER) {
            // SELLER всегда использует свой контекст
            seller = currentUser;
        } else if (currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.MANAGER) {
            // ADMIN/MANAGER могут выбрать селлера
            if (sellerId != null) {
                seller = userRepository.findById(sellerId)
                        .orElseThrow(() -> new UserException(
                                "Селлер не найден с ID: " + sellerId,
                                HttpStatus.NOT_FOUND
                        ));
                validateCanViewSellerAnalytics(currentUser, seller);
            } else {
                // Если sellerId не указан, берем последнего добавленного активного селлера
                seller = getLastActiveSeller(currentUser);
                if (seller == null) {
                    throw new UserException(
                            "Не найдено активных селлеров для просмотра аналитики",
                            HttpStatus.NOT_FOUND
                    );
                }
            }
        } else {
            throw new UserException(
                    "Только SELLER, MANAGER или ADMIN могут просматривать аналитику",
                    HttpStatus.FORBIDDEN
            );
        }

        WbApiKey apiKey = wbApiKeyService.findByUserId(seller.getId());
        return new SellerContext(seller, apiKey.getApiKey());
    }

    /**
     * Создает контекст продавца из данных аутентификации (без указания sellerId).
     * Для SELLER использует текущего пользователя.
     * Для ADMIN/MANAGER использует последнего активного селлера.
     *
     * @param authentication данные аутентификации
     * @return контекст продавца
     */
    public SellerContext createContext(Authentication authentication) {
        return createContext(authentication, null);
    }

    /**
     * Получает последнего добавленного активного селлера с API ключом для текущего пользователя.
     */
    private User getLastActiveSeller(User currentUser) {
        List<User> sellers;
        if (currentUser.getRole() == Role.ADMIN) {
            sellers = userRepository.findByRoleAndIsActive(Role.SELLER, true);
        } else if (currentUser.getRole() == Role.MANAGER) {
            sellers = userRepository.findByRoleAndOwnerId(Role.SELLER, currentUser.getId());
            sellers = sellers.stream()
                    .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                    .toList();
        } else {
            return null;
        }

        // Фильтруем только селлеров с API ключами
        List<User> sellersWithApiKeys = sellers.stream()
                .filter(seller -> {
                    // Проверяем наличие API ключа для селлера
                    return wbApiKeyRepository.findByUserId(seller.getId()).isPresent();
                })
                .toList();

        // Возвращаем последнего добавленного (по createdAt DESC)
        return sellersWithApiKeys.stream()
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .findFirst()
                .orElse(null);
    }

    /**
     * Проверяет, может ли текущий пользователь просматривать аналитику указанного селлера.
     */
    private void validateCanViewSellerAnalytics(User currentUser, User seller) {
        if (seller.getRole() != Role.SELLER) {
            throw new UserException(
                    "Указанный пользователь не является селлером",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (!Boolean.TRUE.equals(seller.getIsActive())) {
            throw new UserException(
                    "Нельзя просматривать аналитику неактивного селлера",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (currentUser.getRole() == Role.ADMIN) {
            // ADMIN может просматривать аналитику любого активного селлера
            return;
        } else if (currentUser.getRole() == Role.MANAGER) {
            // MANAGER может просматривать аналитику только своих селлеров
            if (seller.getOwner() == null || !currentUser.getId().equals(seller.getOwner().getId())) {
                throw new UserException(
                        "MANAGER может просматривать аналитику только своих селлеров",
                        HttpStatus.FORBIDDEN
                );
            }
        } else {
            throw new UserException(
                    "Только MANAGER или ADMIN могут просматривать аналитику других селлеров",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    /**
     * Контекст продавца.
     */
    public record SellerContext(User user, String apiKey) {
    }
}

