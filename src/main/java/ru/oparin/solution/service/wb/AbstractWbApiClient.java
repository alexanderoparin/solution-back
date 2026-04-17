package ru.oparin.solution.service.wb;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.*;
import ru.oparin.solution.dto.wb.WbApiProblemResponse;
import ru.oparin.solution.dto.wb.WbApiSimpleErrorResponse;
import ru.oparin.solution.exception.WbApiUnauthorizedScopeException;
import ru.oparin.solution.exception.WbRateLimitDeferException;

import java.net.URI;
import java.net.UnknownHostException;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

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
    private static final String UNKNOWN_ENDPOINT = "UNKNOWN_ENDPOINT";
    private static final String UNKNOWN_OPERATION = "unknown-operation";
    private static final ConcurrentMap<String, AtomicLong> TOO_MANY_REQUESTS_BY_ENDPOINT = new ConcurrentHashMap<>();
    private static final ConcurrentMap<WbApiCategory, AtomicLong> TOO_MANY_REQUESTS_BY_CATEGORY = new ConcurrentHashMap<>();

    protected RestTemplate restTemplate;
    protected final ObjectMapper objectMapper;

    private WbEndpointRateLimitCoordinator wbEndpointRateLimitCoordinator;

    protected AbstractWbApiClient() {
        this.objectMapper = createObjectMapper();
    }

    @Autowired(required = false)
    void setWbEndpointRateLimitCoordinator(WbEndpointRateLimitCoordinator wbEndpointRateLimitCoordinator) {
        this.wbEndpointRateLimitCoordinator = wbEndpointRateLimitCoordinator;
    }

    @PostConstruct
    void initRestTemplate() {
        this.restTemplate = createRestTemplateWithRateLimitInterceptor();
    }

    private RestTemplate createRestTemplateWithRateLimitInterceptor() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        RestTemplate rt = new RestTemplate(requestFactory);
        WbEndpointRateLimitCoordinator coord = this.wbEndpointRateLimitCoordinator;
        rt.getInterceptors().add((HttpRequest request, byte[] body, ClientHttpRequestExecution execution) -> {
            String auth = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            URI uri = request.getURI();
            String endpointKey = toRateLimitEndpointKey(uri);
            if (coord != null && auth != null && !auth.isBlank() && !endpointKey.isBlank()) {
                coord.beforeRequest(auth, endpointKey, getApiCategory());
            }
            ClientHttpResponse response = execution.execute(request, body);
            if (coord != null && auth != null && !auth.isBlank() && !endpointKey.isBlank()) {
                int status = response.getStatusCode().value();
                HttpHeaders rh = response.getHeaders();
                coord.afterResponse(auth, endpointKey, status, rh, getApiCategory());
                Wb429RateLimitHeadersLogger.logRateLimitHeaders(log, endpointKey, status, rh);
            }
            return response;
        });
        return rt;
    }

    /**
     * Ключ endpoint для лимитов WB (host + path), совпадает с {@link WbEndpointRateLimitCoordinator#endpointKeyFromUrl(String)}.
     */
    protected final String toRateLimitEndpointKey(URI uri) {
        if (uri == null) {
            return "";
        }
        return WbEndpointRateLimitCoordinator.endpointKeyFromUrl(uri.toString());
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    // --- API для наследников ---

    protected abstract WbApiCategory getApiCategory();

    /**
     * Выполняет вызов с учётом сети/504: без ожидания в потоке — при необходимости повтора выбрасывается
     * {@link WbRateLimitDeferException} (события / повтор снаружи). 4xx (401, 429 и т.д.) не ретраит.
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
                Throwable cause = e.getCause();
                if (cause instanceof HttpClientErrorException hce) {
                    throw hce;
                }
                // Ошибка не из класса «сеть / 504» — не ретраить (иначе 4xx в RestClientException дают 3 лишних вызова WB).
                if (!is504GatewayTimeout(e) && !isTimeoutOrConnectionError(e)) {
                    throw e;
                }
                RetryDecision decision = decideRetry(e, attemptNum);
                if (decision != RetryDecision.GIVE_UP) {
                    logRetryAndDefer(decision, context, attemptNum);
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

    protected <T> ResponseEntity<String> executeWithRetry(
            String url,
            String apiKey,
            T requestBody,
            int maxRetries,
            long retryDelayMs,
            String operationName
    ) {
        String endpoint = extractEndpointPath(url);
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpEntity<T> entity = new HttpEntity<>(requestBody, createJsonAuthHeaders(apiKey));
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

                if (response.getStatusCode().value() == 429 && attempt < maxRetries) {
                    log429Metric(endpoint, operationName);
                    log.warn("Получен 429 Too Many Requests (попытка {}/{}). Отложенный повтор через {} мс (без sleep).",
                            attempt, maxRetries, retryDelayMs);
                    throwDeferAfterMillis("WB API 429 Too Many Requests", retryDelayMs);
                }
                if (response.getStatusCode().value() == 429) {
                    log429Metric(endpoint, operationName);
                    throw new RestClientException("429 Too Many Requests: " + response.getBody());
                }
                validateResponse(response);
                return response;
            } catch (RestClientException e) {
                if (is429Error(e) && attempt < maxRetries) {
                    log429Metric(endpoint, operationName);
                    throwDeferAfterMillis("WB API 429 Too Many Requests", retryDelayMs);
                }
                throw e;
            }
        }
        throw new RestClientException("Не удалось выполнить запрос после " + maxRetries + " попыток");
    }

    protected void throwIf401ScopeNotAllowed(HttpClientErrorException e) {
        if (e.getStatusCode().value() == 401) {
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
        logWbApiError(context, e, UNKNOWN_ENDPOINT, UNKNOWN_OPERATION);
    }

    protected void logWbApiError(String context, HttpClientErrorException e, String endpoint, String operationName) {
        String body = e.getResponseBodyAsString();
        int status = e.getStatusCode().value();
        String statusText = e.getStatusText();
        if (status == 429) {
            log429Metric(endpoint, operationName);
        }

        if (body.isBlank()) {
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

    protected void log429Metric() {
        log429Metric(UNKNOWN_ENDPOINT, UNKNOWN_OPERATION);
    }

    protected void log429Metric(String endpoint, String operationName) {
        long count = TOO_MANY_REQUESTS_BY_CATEGORY
                .computeIfAbsent(getApiCategory(), ignored -> new AtomicLong())
                .incrementAndGet();
        String endpointValue = endpoint == null || endpoint.isBlank() ? UNKNOWN_ENDPOINT : endpoint;
        String operationValue = operationName == null || operationName.isBlank() ? UNKNOWN_OPERATION : operationName;
        long endpointCount = TOO_MANY_REQUESTS_BY_ENDPOINT
                .computeIfAbsent(endpointValue, ignored -> new AtomicLong())
                .incrementAndGet();
        log.warn("Метрика 429: endpoint={}, operation={}, category={}, endpoint429={}, total429={}",
                endpointValue, operationValue, getApiCategory(), endpointCount, count);
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
        if (e == null) {
            return false;
        }
        if (e instanceof ResourceAccessException) {
            return true;
        }
        if (hasTimeoutOrConnectionInMessage(e.getMessage())) {
            return true;
        }
        return isTimeoutOrConnectionError(e.getCause());
    }

    /**
     * Логирует I/O (сеть, DNS) ошибки одной строкой без стектрейса; остальные — с полным стеком.
     */
    protected void logIoErrorOrFull(String context, Throwable e) {
        if (isConnectionIoError(e)) {
            log.warn("Ошибка при {}: {}", context, e.getMessage());
        } else {
            log.error("Ошибка при {}: {}", context, e.getMessage(), e);
        }
    }

    /**
     * Проверяет, является ли ошибка сетевой/I/O (DNS, таймаут соединения и т.п.).
     * Используется для логирования без стектрейса в вызывающем коде.
     */
    public static boolean isConnectionIoError(Throwable e) {
        if (e == null) return false;
        if (e instanceof ResourceAccessException || e instanceof UnknownHostException) return true;
        return isConnectionIoError(e.getCause());
    }

    /**
     * Отложить повтор без блокировки потока (события / вызывающий код обрабатывают {@link WbRateLimitDeferException}).
     */
    protected static void throwDeferAfterMillis(String reason, long delayMs) {
        long ms = Math.max(1L, delayMs);
        throw WbRateLimitDeferException.untilEpochMilli(reason, System.currentTimeMillis() + ms);
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
        return he.getStatusCode().value() == 504;
    }

    private void logRetryAndDefer(RetryDecision decision, String context, int attemptNum) {
        if (decision == RetryDecision.RETRY_504) {
            log.warn("504 Gateway Timeout при {} (попытка {}/{}). Отложенный повтор через {} мс (без sleep).",
                    context, attemptNum + 1, MAX_CONNECTION_RETRIES, CONNECTION_RETRY_DELAY_MS);
        } else {
            log.warn("Таймаут/ошибка соединения при {} (попытка {}/{}). Отложенный повтор через {} мс (без sleep).",
                    context, attemptNum + 1, MAX_CONNECTION_RETRIES, CONNECTION_RETRY_DELAY_MS);
        }
        throwDeferAfterMillis("WB API: " + context + " — временная ошибка сети или 504", CONNECTION_RETRY_DELAY_MS);
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

    protected String extractEndpointPath(String url) {
        if (url == null || url.isBlank()) {
            return UNKNOWN_ENDPOINT;
        }
        try {
            URI uri = URI.create(url);
            return uri.getPath() != null && !uri.getPath().isBlank() ? uri.getPath() : UNKNOWN_ENDPOINT;
        } catch (IllegalArgumentException ignored) {
            return UNKNOWN_ENDPOINT;
        }
    }
}
