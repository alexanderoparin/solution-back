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
import ru.oparin.solution.exception.WbRateLimitDeferException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.WbApiBaseUrl;
import ru.oparin.solution.service.wb.Wb429RateLimitHeadersLogger;
import ru.oparin.solution.service.wb.WbApiCategory;
import ru.oparin.solution.service.wb.WbEndpointRateLimitCoordinator;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
    private final CabinetScopeStatusService cabinetScopeStatusService;
    private final WbEndpointRateLimitCoordinator wbEndpointRateLimitCoordinator;

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
        PING_URLS.put(WbApiCategory.CONTENT, WbApiBaseUrl.CONTENT.getPingUrl());
        PING_URLS.put(WbApiCategory.ANALYTICS, WbApiBaseUrl.ANALYTICS.getPingUrl());
        PING_URLS.put(WbApiCategory.PRICES_AND_DISCOUNTS, WbApiBaseUrl.DISCOUNTS_PRICES.getPingUrl());
        PING_URLS.put(WbApiCategory.STATISTICS, WbApiBaseUrl.STATISTICS.getPingUrl());
        PING_URLS.put(WbApiCategory.PROMOTION, WbApiBaseUrl.PROMOTION.getPingUrl());
        PING_URLS.put(WbApiCategory.FEEDBACKS_AND_QUESTIONS, WbApiBaseUrl.FEEDBACKS.getPingUrl());
        PING_URLS.put(WbApiCategory.MARKETPLACE, WbApiBaseUrl.MARKETPLACE.getPingUrl());
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

        /*
         * Признак «ключ валиден» (поля кабинета is_valid / validation_error) задаёт только
         * GET common-api.wildberries.ru/ping — см. документацию WB. Дальше — детализация по категориям в cabinet_scope_status.
         */
        validateTokenByCommonPing(cabinet);
        checkScopesWithPing(cabinet);
    }

    /**
     * Проверка токена через официальный «общий» ping Wildberries (common-api).
     * Результат записывает в {@link Cabinet#setIsValid(Boolean)} и {@link Cabinet#setValidationError(String)}.
     */
    private void validateTokenByCommonPing(Cabinet cabinet) {
        Long cabinetId = cabinet.getId();
        String apiKey = cabinet.getApiKey();
        String url = WbApiBaseUrl.COMMON.getPingUrl();
        String endpointKey = WbEndpointRateLimitCoordinator.endpointKeyFromUrl(url);
        WbApiCategory category = WbApiCategory.COMMON;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", apiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            log.info("Проверка WB API ключа кабинета {} через common-api /ping: {}", cabinetId, url);

            try {
                wbEndpointRateLimitCoordinator.beforeRequest(apiKey, endpointKey, category);
            } catch (WbRateLimitDeferException e) {
                int retryAfter = (int) Math.min(
                        Integer.MAX_VALUE,
                        Math.max(1L, ChronoUnit.SECONDS.between(LocalDateTime.now(), e.getDeferUntil()))
                );
                throw new UserException(
                        "Лимит WB: повторите проверку ключа позже.",
                        HttpStatus.TOO_MANY_REQUESTS,
                        retryAfter
                );
            }

            ResponseEntity<String> response = pingRestTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            wbEndpointRateLimitCoordinator.afterResponse(apiKey, endpointKey, response.getStatusCode().value(),
                    response.getHeaders(), category);
            Wb429RateLimitHeadersLogger.logRateLimitHeaders(log, endpointKey, response.getStatusCode().value(),
                    response.getHeaders());

            if (response.getStatusCode().is2xxSuccessful()) {
                updateValidationStatus(cabinet, true, null);
                log.info("WB API ключ для кабинета {} признан валидным (common-api /ping)", cabinetId);
                return;
            }
            String msg = "Код ответа: " + response.getStatusCode().value();
            updateValidationStatus(cabinet, false, msg);
            log.warn("common-api /ping для кабинета {}: неуспешный статус {}", cabinetId, response.getStatusCode());
        } catch (HttpClientErrorException e) {
            wbEndpointRateLimitCoordinator.afterResponse(apiKey, endpointKey, e.getStatusCode().value(),
                    e.getResponseHeaders(), category);
            Wb429RateLimitHeadersLogger.logRateLimitHeaders(log, endpointKey, e.getStatusCode().value(),
                    e.getResponseHeaders());
            if (e.getStatusCode() != null && e.getStatusCode().value() == 429) {
                throw new UserException(
                        "Слишком частая проверка токена к WB API. Попробуйте ещё раз через 30 секунд.",
                        HttpStatus.TOO_MANY_REQUESTS
                );
            }
            String msg = extractUserFriendlyErrorMessage(e);
            updateValidationStatus(cabinet, false, msg);
            log.warn("common-api /ping для кабинета {}: HTTP {}", cabinetId, e.getStatusCode());
        } catch (RestClientException e) {
            HttpClientErrorException hce = findHttpClientErrorInChain(e);
            String msg = hce != null ? extractUserFriendlyErrorMessage(hce) : resolveFriendlyValidationMessage(e);
            updateValidationStatus(cabinet, false, msg);
            log.warn("common-api /ping для кабинета {}: {}", cabinetId, e.getMessage());
        } catch (UserException e) {
            throw e;
        } catch (Exception e) {
            String msg = resolveFriendlyValidationMessage(e);
            updateValidationStatus(cabinet, false, msg);
            log.warn("common-api /ping для кабинета {}: {}", cabinetId, e.getMessage());
        }
    }

    /**
     * Пошаговая проверка токена через /ping для основных категорий WB API и запись результата в cabinet_scope_status.
     * Общий признак валидности ключа задаётся только {@link #validateTokenByCommonPing(Cabinet)}.
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
     *
     * @return {@code true}, если WB вернул успешный HTTP-код для /ping
     */
    private boolean pingCategoryAndRecordStatus(
            Long cabinetId,
            String apiKey,
            WbApiCategory category,
            String url
    ) {
        String endpointKey = WbEndpointRateLimitCoordinator.endpointKeyFromUrl(url);
        try {
            HttpHeaders headers = new HttpHeaders();
            // Для /ping WB API принимает тот же заголовок Authorization, что и для обычных методов.
            headers.set("Authorization", apiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            log.info("Проверка доступа к категории WB API {} для кабинета {} через ping: {}", category.getDisplayName(), cabinetId, url);

            try {
                wbEndpointRateLimitCoordinator.beforeRequest(apiKey, endpointKey, category);
            } catch (WbRateLimitDeferException e) {
                int retryAfter = (int) Math.min(
                        Integer.MAX_VALUE,
                        Math.max(1L, ChronoUnit.SECONDS.between(LocalDateTime.now(), e.getDeferUntil()))
                );
                cabinetScopeStatusService.recordFailure(cabinetId, category, e.getMessage());
                throw new UserException(
                        "Лимит WB: повторите проверку доступа к категории позже.",
                        HttpStatus.TOO_MANY_REQUESTS,
                        retryAfter
                );
            }
            ResponseEntity<String> response = pingRestTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            wbEndpointRateLimitCoordinator.afterResponse(apiKey, endpointKey, response.getStatusCode().value(), response.getHeaders(), category);
            Wb429RateLimitHeadersLogger.logRateLimitHeaders(log, endpointKey, response.getStatusCode().value(), response.getHeaders());

            if (response.getStatusCode().is2xxSuccessful()) {
                cabinetScopeStatusService.recordSuccess(cabinetId, category);
                return true;
            }
            String msg = "Код ответа: " + response.getStatusCode().value();
            cabinetScopeStatusService.recordFailure(cabinetId, category, msg);
            return false;
        } catch (HttpClientErrorException e) {
            wbEndpointRateLimitCoordinator.afterResponse(apiKey, endpointKey, e.getStatusCode().value(), e.getResponseHeaders(), category);
            Wb429RateLimitHeadersLogger.logRateLimitHeaders(log, endpointKey, e.getStatusCode().value(), e.getResponseHeaders());
            if (e.getStatusCode() != null && e.getStatusCode().value() == 429) {
                // Лимит: максимум 3 запроса за 30 секунд на метод/домен — отдадим понятную ошибку пользователю.
                throw new UserException(
                        "Слишком частая проверка токена к WB API. Попробуйте ещё раз через 30 секунд.",
                        HttpStatus.TOO_MANY_REQUESTS
                );
            }
            String msg = extractUserFriendlyErrorMessage(e);
            cabinetScopeStatusService.recordFailure(cabinetId, category, msg);
            log.warn("Ping категории WB API {} для кабинета {} завершился ошибкой: статус={}, тело={}",
                    category.getDisplayName(), cabinetId, e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (RestClientException e) {
            HttpClientErrorException hce = findHttpClientErrorInChain(e);
            String msg = hce != null ? extractUserFriendlyErrorMessage(hce) : resolveFriendlyValidationMessage(e);
            cabinetScopeStatusService.recordFailure(cabinetId, category, msg);
            log.warn("Ping категории WB API {} для кабинета {} завершился ошибкой соединения: {}",
                    category.getDisplayName(), cabinetId, e.getMessage());
            return false;
        } catch (UserException e) {
            throw e;
        } catch (Exception e) {
            String msg = resolveFriendlyValidationMessage(e);
            cabinetScopeStatusService.recordFailure(cabinetId, category, msg);
            log.warn("Неожиданная ошибка при ping категории WB API {} для кабинета {}: {}",
                    category.getDisplayName(), cabinetId, e.getMessage());
            return false;
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

    /**
     * Ищет в цепочке причин исключение HTTP-клиента WB (для размотки обёрток RestClientException и пр.).
     */
    private HttpClientErrorException findHttpClientErrorInChain(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof HttpClientErrorException hce) {
                return hce;
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * Человекочитаемое сообщение для любого сбоя валидации без утечки тела ответа WB в UI.
     */
    private String resolveFriendlyValidationMessage(Throwable e) {
        HttpClientErrorException hce = findHttpClientErrorInChain(e);
        if (hce != null) {
            return extractUserFriendlyErrorMessage(hce);
        }
        return "Не удалось проверить API ключ. Проверьте подключение и попробуйте снова.";
    }

    /**
     * Преобразует ответ WB с кодом статуса в короткое сообщение для пользователя (без JSON и технических деталей).
     */
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
