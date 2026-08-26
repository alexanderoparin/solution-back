package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.MarketplaceType;
import ru.oparin.solution.service.ozon.OzonApiCategory;
import ru.oparin.solution.service.ozon.OzonPerformanceApiClient;

import java.time.LocalDateTime;

/**
 * Валидация Ozon Performance API credentials через запрос OAuth-токена.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OzonPerformanceCredentialsService {

    private final CabinetService cabinetService;
    private final OzonPerformanceApiClient performanceApiClient;
    private final CabinetScopeStatusService cabinetScopeStatusService;

    /**
     * Проверяет Performance credentials кабинета и обновляет поля валидации.
     */
    @Transactional
    public void validateByCabinet(Cabinet cabinet) {
        if (cabinet.getMarketplaceType() != MarketplaceType.OZON) {
            throw new UserException("Кабинет не является Ozon", HttpStatus.BAD_REQUEST);
        }
        String clientId = cabinet.getOzonPerformanceClientId();
        String clientSecret = cabinet.getOzonPerformanceClientSecret();
        if (clientId == null || clientId.isBlank()) {
            updateValidationStatus(cabinet, false, "Performance client_id не задан");
            cabinetScopeStatusService.recordFailure(
                    cabinet.getId(), OzonApiCategory.PERFORMANCE, "Performance client_id не задан");
            return;
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            updateValidationStatus(cabinet, false, "Performance client_secret не задан");
            cabinetScopeStatusService.recordFailure(
                    cabinet.getId(), OzonApiCategory.PERFORMANCE, "Performance client_secret не задан");
            return;
        }

        Long cabinetId = cabinet.getId();
        try {
            log.info("Проверка Ozon Performance credentials кабинета {}, client_id={}", cabinetId, clientId.trim());
            performanceApiClient.requestToken(clientId.trim(), clientSecret.trim());
            performanceApiClient.invalidateTokenCache(cabinetId);
            updateValidationStatus(cabinet, true, null);
            cabinetScopeStatusService.recordSuccess(cabinetId, OzonApiCategory.PERFORMANCE);
            log.info("Ozon Performance credentials для кабинета {} валидны", cabinetId);
        } catch (HttpClientErrorException e) {
            String msg = toUserMessage(e);
            updateValidationStatus(cabinet, false, msg);
            cabinetScopeStatusService.recordFailure(cabinetId, OzonApiCategory.PERFORMANCE, msg);
            log.warn("Ozon Performance token для кабинета {}: HTTP {}", cabinetId, e.getStatusCode());
        } catch (RestClientException e) {
            String msg = "Ошибка связи с Ozon Performance API. Попробуйте позже.";
            updateValidationStatus(cabinet, false, msg);
            cabinetScopeStatusService.recordFailure(cabinetId, OzonApiCategory.PERFORMANCE, msg);
            log.warn("Ozon Performance token для кабинета {}: {}", cabinetId, e.getMessage());
        } catch (Exception e) {
            String msg = "Ошибка при проверке Performance credentials Ozon.";
            updateValidationStatus(cabinet, false, msg);
            cabinetScopeStatusService.recordFailure(cabinetId, OzonApiCategory.PERFORMANCE, msg);
            log.warn("Ozon Performance token для кабинета {}: {}", cabinetId, e.getMessage());
        }
    }

    /**
     * Проверяет, что у кабинета заданы Performance credentials (без проверки is_valid).
     */
    public boolean hasConfiguredCredentials(Cabinet cabinet) {
        if (cabinet == null) {
            return false;
        }
        String clientId = cabinet.getOzonPerformanceClientId();
        String clientSecret = cabinet.getOzonPerformanceClientSecret();
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    /**
     * Проверяет, что у кабинета заданы и валидны Performance credentials.
     */
    public boolean hasUsableCredentials(Cabinet cabinet) {
        return hasConfiguredCredentials(cabinet)
                && Boolean.TRUE.equals(cabinet.getOzonPerformanceIsValid());
    }

    private static String toUserMessage(HttpClientErrorException e) {
        int status = e.getStatusCode().value();
        if (status == HttpStatus.UNAUTHORIZED.value()
                || status == HttpStatus.FORBIDDEN.value()
                || status == HttpStatus.BAD_REQUEST.value()) {
            return "Performance client_id или client_secret невалидны. Проверьте данные в кабинете рекламы Ozon.";
        }
        if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return "Превышен лимит запросов к Ozon Performance API. Повторите позже.";
        }
        return "Ошибка проверки Ozon Performance API (HTTP " + status + ").";
    }

    private void updateValidationStatus(Cabinet cabinet, boolean isValid, String errorMessage) {
        cabinet.setOzonPerformanceIsValid(isValid);
        cabinet.setOzonPerformanceLastValidatedAt(LocalDateTime.now());
        cabinet.setOzonPerformanceValidationError(isValid ? null : (errorMessage != null ? errorMessage : "Credentials не прошли проверку"));
        cabinetService.save(cabinet);
    }
}
