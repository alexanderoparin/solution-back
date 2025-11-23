package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
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
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserException("Пользователь с таким email уже существует: " + request.getEmail(), 
                    HttpStatus.CONFLICT);
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.SELLER)
                .isActive(true)
                .build();

        user = userRepository.save(user);

        WbApiKey apiKey = WbApiKey.builder()
                .user(user)
                .apiKey(request.getWbApiKey())
                .isValid(null)
                .build();

        wbApiKeyRepository.save(apiKey);

        return user;
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
                .orElseThrow(() -> new UserException("Пользователь не найден: " + email, HttpStatus.NOT_FOUND));
    }

    /**
     * Обновление WB API ключа пользователя.
     *
     * @param userId ID пользователя
     * @param newApiKey новый API ключ
     * @throws UserException если API ключ не найден
     */
    @Transactional
    public void updateApiKey(Long userId, String newApiKey) {
        WbApiKey apiKey = wbApiKeyRepository.findByUserId(userId)
                .orElseThrow(() -> new UserException("API ключ не найден для пользователя с ID: " + userId, HttpStatus.NOT_FOUND));

        apiKey.setApiKey(newApiKey);
        apiKey.setIsValid(null);
        apiKey.setValidationError(null);
        apiKey.setLastValidatedAt(null);

        wbApiKeyRepository.save(apiKey);
    }
}

