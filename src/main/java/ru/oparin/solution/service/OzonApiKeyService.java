package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import ru.oparin.solution.exception.OzonRateLimitDeferException;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.MarketplaceType;
import ru.oparin.solution.service.ozon.OzonSellerApiClient;

import java.time.LocalDateTime;

/**
 * Валидация Client-Id + Api-Key Ozon через {@code POST /v1/seller/info}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OzonApiKeyService {

    private final CabinetService cabinetService;
    private final OzonSellerApiClient ozonSellerApiClient;

    /**
     * Проверяет учётные данные Ozon-кабинета и обновляет поля валидации.
     */
    @Transactional
    public void validateApiKeyByCabinet(Cabinet cabinet) {
        if (cabinet.getMarketplaceType() != MarketplaceType.OZON) {
            throw new UserException("Кабинет не является Ozon", HttpStatus.BAD_REQUEST);
        }
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            updateValidationStatus(cabinet, false, "Api-Key не задан");
            return;
        }
        if (cabinet.getOzonClientId() == null || cabinet.getOzonClientId().isBlank()) {
            updateValidationStatus(cabinet, false, "Client-Id не задан");
            return;
        }

        Long cabinetId = cabinet.getId();
        String clientId = cabinet.getOzonClientId().trim();
        String apiKey = cabinet.getApiKey().trim();

        try {
            log.info("Проверка Ozon API ключа кабинета {} через seller/info, Client-Id={}", cabinetId, clientId);
            ozonSellerApiClient.getSellerInfo(clientId, apiKey);
            updateValidationStatus(cabinet, true, null);
            log.info("Ozon API ключ для кабинета {} признан валидным", cabinetId);
        } catch (OzonRateLimitDeferException e) {
            throw new UserException(
                    "Превышен лимит запросов к Ozon API. Повторите проверку позже.",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        } catch (HttpClientErrorException e) {
            String msg = toUserMessage(e);
            updateValidationStatus(cabinet, false, msg);
            log.warn("Ozon seller/info для кабинета {}: HTTP {}", cabinetId, e.getStatusCode());
            if (e.getStatusCode().value() == 429) {
                throw new UserException(msg, HttpStatus.TOO_MANY_REQUESTS);
            }
        } catch (RestClientException e) {
            OzonRateLimitDeferException defer = OzonRateLimitDeferException.findInChain(e);
            if (defer != null) {
                throw new UserException(
                        "Превышен лимит запросов к Ozon API. Повторите проверку позже.",
                        HttpStatus.TOO_MANY_REQUESTS
                );
            }
            String msg = "Ошибка связи с Ozon API. Попробуйте позже.";
            updateValidationStatus(cabinet, false, msg);
            log.warn("Ozon seller/info для кабинета {}: {}", cabinetId, e.getMessage());
        } catch (Exception e) {
            String msg = "Ошибка при проверке Api-Key Ozon. Проверьте Client-Id и ключ.";
            updateValidationStatus(cabinet, false, msg);
            log.warn("Ozon seller/info для кабинета {}: {}", cabinetId, e.getMessage());
        }
    }

    private static String toUserMessage(HttpClientErrorException e) {
        int status = e.getStatusCode().value();
        if (status == HttpStatus.UNAUTHORIZED.value()
                || status == HttpStatus.FORBIDDEN.value()
                || status == HttpStatus.NOT_FOUND.value()
                || status == HttpStatus.BAD_REQUEST.value()) {
            return "Client-Id или Api-Key Ozon невалидны. Проверьте данные в кабинете продавца.";
        }
        if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return "Превышен лимит запросов к Ozon API. Повторите попытку позже.";
        }
        return "Ошибка проверки Ozon API (HTTP " + status + ").";
    }

    private void updateValidationStatus(Cabinet cabinet, boolean isValid, String errorMessage) {
        cabinet.setIsValid(isValid);
        cabinet.setLastValidatedAt(LocalDateTime.now());
        cabinet.setValidationError(isValid ? null : (errorMessage != null ? errorMessage : "Ключ не прошел валидацию"));
        cabinetService.save(cabinet);
    }
}
