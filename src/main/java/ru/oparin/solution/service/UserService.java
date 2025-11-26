package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.RegisterRequest;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.User;
import ru.oparin.solution.model.WbApiKey;
import ru.oparin.solution.repository.UserRepository;
import ru.oparin.solution.repository.WbApiKeyRepository;

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
}
