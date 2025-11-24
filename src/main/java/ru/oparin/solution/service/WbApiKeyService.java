package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.WbApiKey;
import ru.oparin.solution.repository.WbApiKeyRepository;

import java.time.LocalDateTime;

/**
 * Сервис для работы с WB API ключами.
 * Связь 1:1 - у каждого SELLER'а только один ключ со всеми правами.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WbApiKeyService {

    private final WbApiKeyRepository wbApiKeyRepository;
    private final WbApiClient wbApiClient;

    /**
     * Поиск API ключа по ID пользователя.
     * Возвращает единственный ключ пользователя (связь 1:1).
     *
     * @param userId ID пользователя (SELLER)
     * @return найденный API ключ
     * @throws UserException если API ключ не найден
     */
    public WbApiKey findByUserId(Long userId) {
        return wbApiKeyRepository.findByUserId(userId)
                .orElseThrow(() -> new UserException("API ключ не найден для пользователя с ID: " + userId, HttpStatus.NOT_FOUND));
    }

    /**
     * Валидация WB API ключа пользователя.
     * Проверяет единственный ключ пользователя (связь 1:1).
     *
     * @param userId ID пользователя (SELLER)
     */
    @Transactional
    public void validateApiKey(Long userId) {
        WbApiKey apiKey = findByUserId(userId);
        
        try {
            boolean isValid = wbApiClient.validateApiKey(apiKey.getApiKey());
            
            apiKey.setIsValid(isValid);
            apiKey.setLastValidatedAt(LocalDateTime.now());
            apiKey.setValidationError(isValid ? null : "Ключ не прошел валидацию");
            
            wbApiKeyRepository.save(apiKey);
            
            if (!isValid) {
                log.warn("WB API ключ для пользователя {} невалиден", userId);
            }
        } catch (Exception e) {
            apiKey.setIsValid(false);
            apiKey.setLastValidatedAt(LocalDateTime.now());
            apiKey.setValidationError("Ошибка при валидации: " + e.getMessage());
            wbApiKeyRepository.save(apiKey);
            
            log.error("Ошибка при валидации WB API ключа для пользователя {}", userId, e);
        }
    }
}

