package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.CreateUserRequest;
import ru.oparin.solution.dto.RegisterRequest;
import ru.oparin.solution.dto.UpdateUserRequest;
import ru.oparin.solution.dto.UserListItemDto;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.User;
import ru.oparin.solution.model.WbApiKey;
import ru.oparin.solution.repository.UserRepository;
import ru.oparin.solution.repository.WbApiKeyRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис для работы с пользователями.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final WbApiKeyRepository wbApiKeyRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Регистрация нового продавца (SELLER).
     *
     * @param request данные для регистрации
     * @return созданный пользователь
     * @throws UserException если пользователь с таким email уже существует
     */
    @Transactional
    public User registerSeller(RegisterRequest request) {
        validateEmailNotExists(request.getEmail());
        
        String encodedPassword = encodePassword(request.getPassword());
        User user = createSellerUser(request.getEmail(), encodedPassword);
        
        return userRepository.save(user);
    }

    /**
     * Поиск пользователя по email.
     *
     * @param email email пользователя
     * @return найденный пользователь
     * @throws UserException если пользователь не найден
     */
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserException(
                        "Пользователь не найден: " + email,
                        HttpStatus.NOT_FOUND
                ));
    }

    /**
     * Обновление WB API ключа пользователя.
     *
     * @param userId ID пользователя (SELLER)
     * @param newApiKey новый API ключ со всеми правами
     * @throws UserException если API ключ не найден
     */
    @Transactional
    public void updateApiKey(Long userId, String newApiKey) {
        WbApiKey apiKey = findApiKeyByUserId(userId);
        resetApiKeyValidation(apiKey);
        apiKey.setApiKey(newApiKey);
        wbApiKeyRepository.save(apiKey);
    }

    /**
     * Смена пароля пользователя.
     *
     * @param userId ID пользователя
     * @param currentPassword текущий пароль
     * @param newPassword новый пароль
     * @throws UserException если текущий пароль неверен или пользователь не найден
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = findUserById(userId);
        validateCurrentPassword(user, currentPassword);
        validateNewPasswordDifferent(user, newPassword);
        
        updateUserPassword(user, newPassword);
        userRepository.save(user);
    }

    /**
     * Проверяет, что пользователь с таким email не существует.
     */
    private void validateEmailNotExists(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new UserException(
                    "Пользователь с таким email уже существует: " + email, 
                    HttpStatus.CONFLICT
            );
        }
    }

    /**
     * Кодирует пароль.
     */
    private String encodePassword(String password) {
        return passwordEncoder.encode(password);
    }

    /**
     * Создает нового пользователя-продавца.
     */
    private User createSellerUser(String email, String encodedPassword) {
        return User.builder()
                .email(email)
                .password(encodedPassword)
                .role(Role.SELLER)
                .isActive(true)
                .build();
    }

    /**
     * Находит пользователя по ID.
     */
    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserException(
                        "Пользователь не найден с ID: " + userId,
                        HttpStatus.NOT_FOUND
                ));
    }

    /**
     * Находит API ключ по ID пользователя.
     */
    private WbApiKey findApiKeyByUserId(Long userId) {
        return wbApiKeyRepository.findByUserId(userId)
                .orElseThrow(() -> new UserException(
                        "API ключ не найден для пользователя с ID: " + userId, 
                        HttpStatus.NOT_FOUND
                ));
    }

    /**
     * Сбрасывает статус валидации API ключа.
     */
    private void resetApiKeyValidation(WbApiKey apiKey) {
        apiKey.setIsValid(null);
        apiKey.setValidationError(null);
        apiKey.setLastValidatedAt(null);
    }

    /**
     * Проверяет текущий пароль пользователя.
     */
    private void validateCurrentPassword(User user, String currentPassword) {
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new UserException("Неверный текущий пароль", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Проверяет, что новый пароль отличается от текущего.
     */
    private void validateNewPasswordDifferent(User user, String newPassword) {
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new UserException("Новый пароль должен отличаться от текущего", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Обновляет пароль пользователя и снимает флаг временного пароля.
     */
    private void updateUserPassword(User user, String newPassword) {
        user.setPassword(encodePassword(newPassword));
        user.setIsTemporaryPassword(false);
    }

    /**
     * Создание нового пользователя с проверкой прав доступа.
     *
     * @param request данные для создания пользователя
     * @param currentUser текущий пользователь (создатель)
     * @return созданный пользователь
     * @throws UserException если нет прав или пользователь с таким email уже существует
     */
    @Transactional
    public User createUser(CreateUserRequest request, User currentUser) {
        validateCanCreateUser(currentUser, request.getRole());
        validateEmailNotExists(request.getEmail());

        String encodedPassword = encodePassword(request.getPassword());
        User newUser = User.builder()
                .email(request.getEmail())
                .password(encodedPassword)
                .role(request.getRole())
                .isActive(true)
                .isTemporaryPassword(true)
                .owner(getOwnerForNewUser(currentUser, request.getRole()))
                .build();

        return userRepository.save(newUser);
    }

    /**
     * Обновление пользователя.
     *
     * @param userId ID пользователя для обновления
     * @param request данные для обновления
     * @param currentUser текущий пользователь
     * @return обновленный пользователь
     * @throws UserException если нет прав или пользователь не найден
     */
    @Transactional
    public User updateUser(Long userId, UpdateUserRequest request, User currentUser) {
        User userToUpdate = findUserById(userId);
        validateCanManageUser(currentUser, userToUpdate);

        // Проверяем, что email не занят другим пользователем
        if (!userToUpdate.getEmail().equals(request.getEmail())) {
            validateEmailNotExists(request.getEmail());
            userToUpdate.setEmail(request.getEmail());
        }

        if (request.getIsActive() != null) {
            userToUpdate.setIsActive(request.getIsActive());
        }

        return userRepository.save(userToUpdate);
    }

    /**
     * Переключение активности пользователя.
     *
     * @param userId ID пользователя
     * @param currentUser текущий пользователь
     * @return обновленный пользователь
     * @throws UserException если нет прав или пользователь не найден
     */
    @Transactional
    public User toggleUserActive(Long userId, User currentUser) {
        User userToUpdate = findUserById(userId);
        validateCanManageUser(currentUser, userToUpdate);

        userToUpdate.setIsActive(!userToUpdate.getIsActive());
        return userRepository.save(userToUpdate);
    }

    /**
     * Получение списка пользователей, которыми может управлять текущий пользователь.
     *
     * @param currentUser текущий пользователь
     * @return список пользователей
     */
    public List<UserListItemDto> getManagedUsers(User currentUser) {
        List<User> users;

        if (currentUser.getRole() == Role.ADMIN) {
            // Админ видит всех менеджеров
            users = userRepository.findByRole(Role.MANAGER);
        } else if (currentUser.getRole() == Role.MANAGER) {
            // Менеджер видит своих селлеров
            users = userRepository.findByRoleAndOwnerId(Role.SELLER, currentUser.getId());
        } else if (currentUser.getRole() == Role.SELLER) {
            // Селлер видит своих работников
            users = userRepository.findByRoleAndOwnerId(Role.WORKER, currentUser.getId());
        } else {
            // WORKER не может управлять пользователями
            return List.of();
        }

        return users.stream()
                .map(this::mapToUserListItemDto)
                .collect(Collectors.toList());
    }

    /**
     * Проверяет, может ли текущий пользователь создавать пользователей указанной роли.
     */
    private void validateCanCreateUser(User currentUser, Role newUserRole) {
        if (currentUser.getRole() == Role.ADMIN && newUserRole != Role.MANAGER) {
            throw new UserException("ADMIN может создавать только MANAGER", HttpStatus.FORBIDDEN);
        }
        if (currentUser.getRole() == Role.MANAGER && newUserRole != Role.SELLER) {
            throw new UserException("MANAGER может создавать только SELLER", HttpStatus.FORBIDDEN);
        }
        if (currentUser.getRole() == Role.SELLER && newUserRole != Role.WORKER) {
            throw new UserException("SELLER может создавать только WORKER", HttpStatus.FORBIDDEN);
        }
        if (currentUser.getRole() == Role.WORKER) {
            throw new UserException("WORKER не может создавать пользователей", HttpStatus.FORBIDDEN);
        }
    }

    /**
     * Проверяет, может ли текущий пользователь управлять указанным пользователем.
     */
    private void validateCanManageUser(User currentUser, User userToManage) {
        if (currentUser.getRole() == Role.ADMIN) {
            // Админ может управлять только менеджерами
            if (userToManage.getRole() != Role.MANAGER) {
                throw new UserException("ADMIN может управлять только MANAGER", HttpStatus.FORBIDDEN);
            }
            // Админ может управлять менеджерами, у которых он владелец
            if (userToManage.getOwner() == null || !currentUser.getId().equals(userToManage.getOwner().getId())) {
                throw new UserException("ADMIN может управлять только своими MANAGER", HttpStatus.FORBIDDEN);
            }
        } else if (currentUser.getRole() == Role.MANAGER) {
            // Менеджер может управлять только своими селлерами
            if (userToManage.getRole() != Role.SELLER) {
                throw new UserException("MANAGER может управлять только SELLER", HttpStatus.FORBIDDEN);
            }
            if (userToManage.getOwner() == null || !currentUser.getId().equals(userToManage.getOwner().getId())) {
                throw new UserException("MANAGER может управлять только своими SELLER", HttpStatus.FORBIDDEN);
            }
        } else if (currentUser.getRole() == Role.SELLER) {
            // Селлер может управлять только своими работниками
            if (userToManage.getRole() != Role.WORKER) {
                throw new UserException("SELLER может управлять только WORKER", HttpStatus.FORBIDDEN);
            }
            if (userToManage.getOwner() == null || !currentUser.getId().equals(userToManage.getOwner().getId())) {
                throw new UserException("SELLER может управлять только своими WORKER", HttpStatus.FORBIDDEN);
            }
        } else {
            throw new UserException("WORKER не может управлять пользователями", HttpStatus.FORBIDDEN);
        }
    }

    /**
     * Получает владельца для нового пользователя в зависимости от роли.
     */
    private User getOwnerForNewUser(User currentUser, Role newUserRole) {
        if (newUserRole == Role.MANAGER) {
            return currentUser; // ADMIN создает MANAGER
        } else if (newUserRole == Role.SELLER) {
            return currentUser; // MANAGER создает SELLER
        } else if (newUserRole == Role.WORKER) {
            return currentUser; // SELLER создает WORKER
        }
        return null;
    }

    /**
     * Преобразует User в UserListItemDto.
     */
    private UserListItemDto mapToUserListItemDto(User user) {
        return UserListItemDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .isActive(user.getIsActive())
                .isTemporaryPassword(user.getIsTemporaryPassword())
                .createdAt(user.getCreatedAt())
                .ownerEmail(user.getOwner() != null ? user.getOwner().getEmail() : null)
                .build();
    }
}
