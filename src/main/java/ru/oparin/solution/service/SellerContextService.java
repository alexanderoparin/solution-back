package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.CabinetRepository;
import ru.oparin.solution.repository.UserRepository;

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
    private final CabinetRepository cabinetRepository;

    /**
     * Создает контекст продавца из данных аутентификации.
     * Для SELLER использует текущего пользователя.
     * Для ADMIN/MANAGER использует выбранного sellerId (если указан) или последнего активного селлера.
     * Кабинет: по умолчанию (последний созданный) или из cabinetId, если передан и принадлежит селлеру.
     *
     * @param authentication данные аутентификации
     * @param sellerId ID селлера (опционально, только для ADMIN/MANAGER)
     * @param cabinetId ID кабинета (опционально; если передан и принадлежит селлеру — используется этот кабинет)
     * @return контекст продавца
     */
    public SellerContext createContext(Authentication authentication, Long sellerId, Long cabinetId) {
        User currentUser = userService.findByEmail(authentication.getName());

        User seller;
        if (currentUser.getRole() == Role.SELLER) {
            seller = currentUser;
        } else if (currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.MANAGER) {
            if (sellerId != null) {
                seller = userRepository.findById(sellerId)
                        .orElseThrow(() -> new UserException(
                                "Селлер не найден с ID: " + sellerId,
                                HttpStatus.NOT_FOUND
                        ));
                validateCanViewSellerAnalytics(currentUser, seller);
            } else {
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

        Cabinet cabinet = resolveCabinet(seller.getId(), cabinetId);
        return new SellerContext(seller, cabinet);
    }

    /**
     * Кабинет: если cabinetId передан и принадлежит селлеру — этот кабинет, иначе кабинет по умолчанию.
     */
    private Cabinet resolveCabinet(Long sellerId, Long cabinetId) {
        if (cabinetId != null && cabinetRepository.existsByIdAndUser_Id(cabinetId, sellerId)) {
            return cabinetRepository.findById(cabinetId)
                    .orElseGet(() -> wbApiKeyService.findDefaultCabinetByUserId(sellerId));
        }
        return wbApiKeyService.findDefaultCabinetByUserId(sellerId);
    }

    /**
     * Создает контекст продавца (без указания cabinetId — используется кабинет по умолчанию).
     */
    public SellerContext createContext(Authentication authentication, Long sellerId) {
        return createContext(authentication, sellerId, null);
    }

    /**
     * Создает контекст продавца (без указания sellerId и cabinetId).
     */
    public SellerContext createContext(Authentication authentication) {
        return createContext(authentication, null, null);
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

        // Фильтруем только селлеров с кабинетами
        List<User> sellersWithCabinets = sellers.stream()
                .filter(seller -> cabinetRepository.findDefaultByUserId(seller.getId()).isPresent())
                .toList();

        // Возвращаем последнего добавленного (по createdAt DESC)
        return sellersWithCabinets.stream()
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
     * Контекст продавца: пользователь-селлер и выбранный кабинет (ключ и id берутся из кабинета).
     */
    public record SellerContext(User user, Cabinet cabinet) {
        public String apiKey() {
            return cabinet != null ? cabinet.getApiKey() : null;
        }

        public Long cabinetId() {
            return cabinet != null ? cabinet.getId() : null;
        }
    }
}

