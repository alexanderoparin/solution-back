package ru.oparin.solution.service.wb;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.*;
import ru.oparin.solution.dto.wb.WbApiProblemResponse;
import ru.oparin.solution.dto.wb.WbApiSimpleErrorResponse;
import ru.oparin.solution.exception.WbApiUnauthorizedScopeException;

import java.util.concurrent.Callable;

/**
 * Абстрактный базовый класс для клиентов WB API.
 * Содержит общие методы для работы с HTTP запросами.
 * Каждый клиент задаёт категорию WB API для сообщений при 401 (token scope not allowed).
 */
@Slf4j
public abstract class AbstractWbApiClient {

    protected static final String BEARER_PREFIX = "Bearer ";
    protected static final int MAX_CONNECTION_RETRIES = 3;
    protected static final long CONNECTION_RETRY_DELAY_MS = 10_000;

    private static final int MAX_BODY_LOG_LENGTH = 500;

    protected final RestTemplate restTemplate;
    protected final ObjectMapper objectMapper;

    protected AbstractWbApiClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = createObjectMapper();
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    // --- API для наследников ---

    protected abstract WbApiCategory getApiCategory();

    /**
     * Выполняет вызов с ретраями при таймауте, ошибке соединения, DNS и при 504.
     * 4xx (401, 429 и т.д.) не ретраит — пробрасываются сразу.
     */
    protected <R> R executeWithConnectionRetry(String context, Callable<R> attempt) {
        RestClientException lastFailure = null;
        int attemptNum = 0;

        while (attemptNum < MAX_CONNECTION_RETRIES) {
            try {
                return attempt.call();
            } catch (RestClientException e) {
                if (e instanceof HttpClientErrorException) {
                    throw e;
                }
                RetryDecision decision = decideRetry(e, attemptNum);
                if (decision != RetryDecision.GIVE_UP) {
                    logRetryAndSleep(decision, context, attemptNum);
                    attemptNum++;
                    continue;
                }
                lastFailure = e;
            } catch (Exception e) {
                rethrowOrWrap(e);
            }
            attemptNum++;
        }

        throw lastFailure != null
                ? lastFailure
                : new RestClientException("Не удалось выполнить запрос после " + MAX_CONNECTION_RETRIES + " попыток");
    }

    protected <T> ResponseEntity<String> executePostRequest(String url, HttpEntity<T> entity) {
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        validateResponse(response);
        return response;
    }

    /**
     * Запрос с ретраями при 429 (Too Many Requests).
     */
    protected <T> ResponseEntity<String> executeWithRetry(
            String url,
            String apiKey,
            T requestBody,
            int maxRetries,
            long retryDelayMs
    ) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpEntity<T> entity = new HttpEntity<>(requestBody, createJsonAuthHeaders(apiKey));
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

                if (response.getStatusCode().value() == 429 && attempt < maxRetries) {
                    log.warn("Получен 429 Too Many Requests (попытка {}/{}). Ожидание {} мс...", attempt, maxRetries, retryDelayMs);
                    sleep(retryDelayMs);
                    continue;
                }
                if (response.getStatusCode().value() == 429) {
                    throw new RestClientException("429 Too Many Requests: " + response.getBody());
                }
                validateResponse(response);
                return response;
            } catch (RestClientException e) {
                if (is429Error(e) && attempt < maxRetries) {
                    sleep(retryDelayMs);
                    continue;
                }
                throw e;
            }
        }
        throw new RestClientException("Не удалось выполнить запрос после " + maxRetries + " попыток");
    }

    protected void throwIf401ScopeNotAllowed(HttpClientErrorException e) {
        if (e.getStatusCode() != null && e.getStatusCode().value() == 401) {
            throw new WbApiUnauthorizedScopeException(e, getApiCategory());
        }
    }

    protected void validateResponse(ResponseEntity<String> response) {
        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("Ошибка от WB API: статус={}", response.getStatusCode());
            throw new RestClientException("Ошибка от WB API: " + response.getStatusCode());
        }
        if (response.getBody() == null || response.getBody().isEmpty()) {
            log.error("Тело ответа от WB API пустое");
            throw new RestClientException("Тело ответа от WB API пустое");
        }
    }

    // --- Логирование и заголовки ---

    protected void logWbApiCall(String url, String purpose) {
        log.info("WB API: {} — {}", url, purpose);
    }

    protected void logWbApiCall(String url, String purpose, Long nmId) {
        if (nmId != null) {
            log.info("WB API: {} — {} nmID={}", url, purpose, nmId);
        } else {
            log.info("WB API: {} — {}", url, purpose);
        }
    }

    /**
     * Логирует ошибку WB API (4xx) без стектрейса: парсит тело и пишет одну строку.
     */
    protected void logWbApiError(String context, HttpClientErrorException e) {
        String body = e.getResponseBodyAsString();
        int status = e.getStatusCode() != null ? e.getStatusCode().value() : 0;
        String statusText = e.getStatusText();

        if (body == null || body.isBlank()) {
            log.error("WB API [{}]: {} {}", context, status, statusText);
            return;
        }
        if (tryLogAsProblemJson(context, status, statusText, body)) {
            return;
        }
        if (tryLogAsSimpleError(context, status, statusText, body)) {
            return;
        }
        log.error("WB API [{}]: {} {}; тело ответа: {}", context, status, statusText, truncateBody(body));
    }

    protected HttpHeaders createAuthHeaders(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", apiKey);
        return headers;
    }

    protected HttpHeaders createAuthHeadersWithBearer(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", apiKey.startsWith(BEARER_PREFIX) ? apiKey : BEARER_PREFIX + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    // --- Ретраи и утилиты (protected для наследников) ---

    protected static boolean isTimeoutOrConnectionError(Throwable e) {
        if (e instanceof ResourceAccessException) {
            return true;
        }
        if (hasTimeoutOrConnectionInMessage(e.getMessage())) {
            return true;
        }
        return e.getCause() != null && isTimeoutOrConnectionError(e.getCause());
    }

    protected static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RestClientException("Прервано ожидание перед повторной попыткой", ie);
        }
    }

    // --- Приватные методы ретраев ---

    private enum RetryDecision { RETRY_504, RETRY_CONNECTION, GIVE_UP }

    private RetryDecision decideRetry(RestClientException e, int attemptNum) {
        if (attemptNum >= MAX_CONNECTION_RETRIES - 1) {
            return RetryDecision.GIVE_UP;
        }
        if (is504GatewayTimeout(e)) {
            return RetryDecision.RETRY_504;
        }
        if (isTimeoutOrConnectionError(e)) {
            return RetryDecision.RETRY_CONNECTION;
        }
        return RetryDecision.GIVE_UP;
    }

    private boolean is504GatewayTimeout(RestClientException e) {
        if (!(e instanceof HttpServerErrorException he)) {
            return false;
        }
        return he.getStatusCode() != null && he.getStatusCode().value() == 504;
    }

    private void logRetryAndSleep(RetryDecision decision, String context, int attemptNum) {
        if (decision == RetryDecision.RETRY_504) {
            log.warn("504 Gateway Timeout при {} (попытка {}/{}). Повтор через {} мс...",
                    context, attemptNum + 1, MAX_CONNECTION_RETRIES, CONNECTION_RETRY_DELAY_MS);
        } else {
            log.warn("Таймаут/ошибка соединения при {} (попытка {}/{}). Повтор через {} мс...",
                    context, attemptNum + 1, MAX_CONNECTION_RETRIES, CONNECTION_RETRY_DELAY_MS);
        }
        sleep(CONNECTION_RETRY_DELAY_MS);
    }

    private static void rethrowOrWrap(Exception e) {
        if (e instanceof RuntimeException re) {
            throw re;
        }
        throw new RestClientException("Ошибка при вызове WB API: " + e.getMessage(), e);
    }

    private HttpHeaders createJsonAuthHeaders(String apiKey) {
        HttpHeaders headers = createAuthHeaders(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private boolean is429Error(RestClientException e) {
        return e.getMessage() != null && e.getMessage().contains("429");
    }

    // --- Приватные методы логирования ошибок API ---

    private static boolean hasTimeoutOrConnectionInMessage(String msg) {
        if (msg == null) {
            return false;
        }
        String lower = msg.toLowerCase();
        return lower.contains("timed out")
                || lower.contains("operation timed out")
                || lower.contains("connection")
                || lower.contains("connect");
    }

    private boolean tryLogAsProblemJson(String context, int status, String statusText, String body) {
        if (!body.contains("\"title\"") || !body.contains("\"detail\"")) {
            return false;
        }
        try {
            WbApiProblemResponse problem = objectMapper.readValue(body, WbApiProblemResponse.class);
            log.error("WB API [{}]: {} {}; title={}, detail={}, requestId={}, origin={}",
                    context, status, statusText,
                    problem.getTitle(), problem.getDetail(),
                    problem.getRequestId(), problem.getOrigin());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean tryLogAsSimpleError(String context, int status, String statusText, String body) {
        try {
            WbApiSimpleErrorResponse simple = objectMapper.readValue(body, WbApiSimpleErrorResponse.class);
            if (Boolean.TRUE.equals(simple.getError()) && simple.getErrorText() != null) {
                log.error("WB API [{}]: {} {}; errorText={}, additionalErrors={}",
                        context, status, statusText, simple.getErrorText(), simple.getAdditionalErrors());
                return true;
            }
        } catch (Exception ignored) {
            // не удалось распарсить
        }
        return false;
    }

    private static String truncateBody(String body) {
        return body.length() > MAX_BODY_LOG_LENGTH ? body.substring(0, MAX_BODY_LOG_LENGTH) + "..." : body;
    }
}
