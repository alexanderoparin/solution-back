package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.service.wb.WbApiCategory;
import ru.oparin.solution.service.wb.WbContentApiClient;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

/**
 * Сервис для работы с WB API ключами (ключ хранится в кабинете).
 * Для пользователя возвращается кабинет по умолчанию (последний созданный).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WbApiKeyService {

    private final CabinetService cabinetService;
    private final WbContentApiClient contentApiClient;
    private final CabinetScopeStatusService cabinetScopeStatusService;

    /**
     * Клиент только для проверок /ping по разным доменам WB API.
     * Используется для быстрой проверки доступа токена к категориям.
     */
    private final RestTemplate pingRestTemplate = new RestTemplate();

    /**
     * URL-ы метода /ping для основных категорий WB API.
     * Взяты из Swagger WB: у каждой категории свой домен.
     */
    private static final Map<WbApiCategory, String> PING_URLS = new EnumMap<>(WbApiCategory.class);

    static {
        PING_URLS.put(WbApiCategory.CONTENT, "https://content-api.wildberries.ru/ping");
        PING_URLS.put(WbApiCategory.ANALYTICS, "https://seller-analytics-api.wildberries.ru/ping");
        PING_URLS.put(WbApiCategory.PRICES_AND_DISCOUNTS, "https://discounts-prices-api.wildberries.ru/ping");
        PING_URLS.put(WbApiCategory.STATISTICS, "https://statistics-api.wildberries.ru/ping");
        PING_URLS.put(WbApiCategory.PROMOTION, "https://advert-api.wildberries.ru/ping");
        PING_URLS.put(WbApiCategory.FEEDBACKS_AND_QUESTIONS, "https://feedbacks-api.wildberries.ru/ping");
        PING_URLS.put(WbApiCategory.MARKETPLACE, "https://marketplace-api.wildberries.ru/ping");
    }

    /**
     * Кабинет по умолчанию для пользователя (последний созданный).
     *
     * @param userId ID пользователя (SELLER)
     * @return кабинет с ключом
     * @throws UserException если кабинет не найден
     */
    public Cabinet findDefaultCabinetByUserId(Long userId) {
        return cabinetService.findDefaultByUserId(userId)
                .orElseThrow(() -> new UserException(
                        "Кабинет не найден для пользователя с ID: " + userId,
                        HttpStatus.NOT_FOUND
                ));
    }

    /**
     * Кабинет по умолчанию для пользователя (Optional).
     */
    public java.util.Optional<Cabinet> findDefaultCabinetByUserIdOptional(Long userId) {
        return cabinetService.findDefaultByUserId(userId);
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
     * Запуск валидации API ключа кабинета.
     */
    @Transactional
    public void validateApiKey(Long cabinetId, Long userId) {
        Cabinet cabinet = cabinetService.findCabinetByIdAndUserId(cabinetId, userId);
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
            // Даже если ключ не задан, для прозрачности помечаем все категории как недоступные.
            markAllCategoriesAsNoAccess(cabinet, "API ключ не задан");
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

        // После базовой проверки ключа (контент) дополнительно проверяем доступ к категориям через /ping.
        // Запросов немного (по одному на домен), они укладываются в лимит 3 запроса за 30 секунд на метод/домен.
        checkScopesWithPing(cabinet);
    }

    /**
     * Пошаговая проверка токена через /ping для основных категорий WB API и запись результата в cabinet_scope_status.
     * Используется на экране профиля в блоке «Доступ к категориям WB API».
     */
    private void checkScopesWithPing(Cabinet cabinet) {
        Long cabinetId = cabinet.getId();
        String apiKey = cabinet.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            markAllCategoriesAsNoAccess(cabinet, "API ключ не задан");
            return;
        }

        for (Map.Entry<WbApiCategory, String> entry : PING_URLS.entrySet()) {
            WbApiCategory category = entry.getKey();
            String url = entry.getValue();
            pingCategoryAndRecordStatus(cabinetId, apiKey, category, url);
        }
    }

    /**
     * Вызывает /ping для конкретной категории и записывает результат в cabinet_scope_status.
     */
    private void pingCategoryAndRecordStatus(Long cabinetId, String apiKey, WbApiCategory category, String url) {
        try {
            HttpHeaders headers = new HttpHeaders();
            // Для /ping WB API принимает тот же заголовок Authorization, что и для обычных методов.
            headers.set("Authorization", apiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            log.info("Проверка доступа к категории WB API {} для кабинета {} через ping: {}", category.getDisplayName(), cabinetId, url);

            ResponseEntity<String> response = pingRestTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                cabinetScopeStatusService.recordSuccess(cabinetId, category);
            } else {
                String msg = "Код ответа: " + response.getStatusCode().value();
                cabinetScopeStatusService.recordFailure(cabinetId, category, msg);
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() != null && e.getStatusCode().value() == 429) {
                // Лимит: максимум 3 запроса за 30 секунд на метод/домен — отдадим понятную ошибку пользователю.
                throw new UserException(
                        "Слишком частая проверка токена к WB API. Попробуйте ещё раз через 30 секунд.",
                        HttpStatus.TOO_MANY_REQUESTS
                );
            }
            String body = e.getResponseBodyAsString();
            String msg = (body != null && !body.isBlank()) ? body : ("HTTP " + e.getStatusCode());
            cabinetScopeStatusService.recordFailure(cabinetId, category, msg);
            log.warn("Ping категории WB API {} для кабинета {} завершился ошибкой: статус={}, тело={}",
                    category.getDisplayName(), cabinetId, e.getStatusCode(), body);
        } catch (RestClientException e) {
            cabinetScopeStatusService.recordFailure(cabinetId, category, e.getMessage());
            log.warn("Ping категории WB API {} для кабинета {} завершился ошибкой соединения: {}",
                    category.getDisplayName(), cabinetId, e.getMessage());
        } catch (Exception e) {
            cabinetScopeStatusService.recordFailure(cabinetId, category, e.getMessage());
            log.warn("Неожиданная ошибка при ping категории WB API {} для кабинета {}: {}",
                    category.getDisplayName(), cabinetId, e.getMessage());
        }
    }

    /**
     * Помечает все поддерживаемые категории как «нет доступа» с указанным сообщением.
     */
    private void markAllCategoriesAsNoAccess(Cabinet cabinet, String message) {
        Long cabinetId = cabinet.getId();
        for (WbApiCategory category : PING_URLS.keySet()) {
            cabinetScopeStatusService.recordFailure(cabinetId, category, message);
        }
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
        cabinetService.save(cabinet);
    }

    private String getErrorMessage(String errorMessage) {
        return errorMessage != null ? errorMessage : "Ключ не прошел валидацию";
    }
}
