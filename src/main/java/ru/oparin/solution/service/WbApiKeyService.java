package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.repository.CabinetRepository;
import ru.oparin.solution.service.wb.WbContentApiClient;

import java.time.LocalDateTime;

/**
 * Сервис для работы с WB API ключами (ключ хранится в кабинете).
 * Для пользователя возвращается кабинет по умолчанию (последний созданный).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WbApiKeyService {

    private final CabinetRepository cabinetRepository;
    private final WbContentApiClient contentApiClient;

    /**
     * Кабинет по умолчанию для пользователя (последний созданный).
     *
     * @param userId ID пользователя (SELLER)
     * @return кабинет с ключом
     * @throws UserException если кабинет не найден
     */
    public Cabinet findDefaultCabinetByUserId(Long userId) {
        return cabinetRepository.findDefaultByUserId(userId)
                .orElseThrow(() -> new UserException(
                        "Кабинет не найден для пользователя с ID: " + userId,
                        HttpStatus.NOT_FOUND
                ));
    }

    /**
     * Кабинет по умолчанию для пользователя (Optional).
     */
    public java.util.Optional<Cabinet> findDefaultCabinetByUserIdOptional(Long userId) {
        return cabinetRepository.findDefaultByUserId(userId);
    }

    /**
     * Валидация WB API ключа кабинета по умолчанию для пользователя.
     *
     * @param userId ID пользователя (SELLER)
     */
    @Transactional
    public void validateApiKey(Long userId) {
        Cabinet cabinet = findDefaultCabinetByUserId(userId);
        validateApiKeyByCabinet(cabinet);
    }

    /**
     * Валидация WB API ключа указанного кабинета.
     *
     * @param cabinet кабинет с ключом для проверки
     */
    @Transactional
    public void validateApiKeyByCabinet(Cabinet cabinet) {
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            updateValidationStatus(cabinet, false, "API ключ не задан");
            return;
        }
        try {
            contentApiClient.ping(cabinet.getApiKey());
            updateValidationStatus(cabinet, true, null);
            log.info("WB API ключ для кабинета {} валиден", cabinet.getId());
        } catch (HttpClientErrorException e) {
            String userFriendlyMessage = extractUserFriendlyErrorMessage(e);
            updateValidationStatus(cabinet, false, userFriendlyMessage);
            log.error("Ошибка при валидации WB API ключа для кабинета {}: статус={}, сообщение={}",
                    cabinet.getId(), e.getStatusCode(), e.getMessage());
        } catch (Exception e) {
            String errorMessage = "Ошибка при валидации: " + e.getMessage();
            updateValidationStatus(cabinet, false, errorMessage);
            log.error("Ошибка при валидации WB API ключа для кабинета {}", cabinet.getId(), e);
        }
    }

    /**
     * Сбрасывает статус валидации и устанавливает новый API ключ кабинета.
     */
    @Transactional
    public void resetValidationAndSetApiKey(Cabinet cabinet, String apiKey) {
        cabinet.setIsValid(null);
        cabinet.setValidationError(null);
        cabinet.setLastValidatedAt(null);
        cabinet.setApiKey(apiKey != null ? apiKey.trim() : null);
    }

    private String extractUserFriendlyErrorMessage(HttpClientErrorException e) {
        HttpStatusCode statusCode = e.getStatusCode();
        if (statusCode.value() == 401) {
            return "API ключ невалиден или истек. Проверьте правильность ключа и его срок действия.";
        } else if (statusCode.value() == 403) {
            return "API ключ не имеет необходимых прав доступа. Убедитесь, что ключ имеет все требуемые разрешения.";
        } else if (statusCode.value() == 429) {
            return "Превышен лимит запросов к API. Попробуйте позже.";
        } else if (statusCode.is5xxServerError()) {
            return "Ошибка на стороне сервера Wildberries. Попробуйте позже.";
        } else {
            return "Ошибка при проверке API ключа. Проверьте правильность ключа и попробуйте снова.";
        }
    }

    private void updateValidationStatus(Cabinet cabinet, boolean isValid, String errorMessage) {
        cabinet.setIsValid(isValid);
        cabinet.setLastValidatedAt(LocalDateTime.now());
        cabinet.setValidationError(isValid ? null : getErrorMessage(errorMessage));
        cabinetRepository.save(cabinet);
    }

    private String getErrorMessage(String errorMessage) {
        return errorMessage != null ? errorMessage : "Ключ не прошел валидацию";
    }
}
