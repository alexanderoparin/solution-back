package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.WbApiKey;
import ru.oparin.solution.repository.WbApiKeyRepository;
import ru.oparin.solution.service.wb.WbContentApiClient;

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
    private final WbContentApiClient contentApiClient;

    /**
     * Поиск API ключа по ID пользователя.
     *
     * @param userId ID пользователя (SELLER)
     * @return найденный API ключ
     * @throws UserException если API ключ не найден
     */
    public WbApiKey findByUserId(Long userId) {
        return wbApiKeyRepository.findByUserId(userId)
                .orElseThrow(() -> new UserException(
                        "API ключ не найден для пользователя с ID: " + userId,
                        HttpStatus.NOT_FOUND
                ));
    }

    /**
     * Валидация WB API ключа пользователя.
     *
     * @param userId ID пользователя (SELLER)
     */
    @Transactional
    public void validateApiKey(Long userId) {
        WbApiKey apiKey = findByUserId(userId);

        try {
            contentApiClient.ping(apiKey.getApiKey());
            updateValidationStatus(apiKey, true, null);
            log.info("WB API ключ для пользователя {} валиден", userId);
        } catch (Exception e) {
            String errorMessage = "Ошибка при валидации: " + e.getMessage();
            updateValidationStatus(apiKey, false, errorMessage);
            log.error("Ошибка при валидации WB API ключа для пользователя {}", userId, e);
        }
    }

    private void updateValidationStatus(WbApiKey apiKey, boolean isValid, String errorMessage) {
        apiKey.setIsValid(isValid);
        apiKey.setLastValidatedAt(LocalDateTime.now());
        apiKey.setValidationError(isValid ? null : getErrorMessage(errorMessage));
        wbApiKeyRepository.save(apiKey);
    }

    private String getErrorMessage(String errorMessage) {
        return errorMessage != null ? errorMessage : "Ключ не прошел валидацию";
    }
}
