package ru.oparin.solution.service.wb;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import ru.oparin.solution.dto.wb.*;
import ru.oparin.solution.model.CabinetTokenType;
import ru.oparin.solution.model.WbApiEventType;
import ru.oparin.solution.service.PromotionCampaignControlWriteService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * Клиент для работы с Promotion API Wildberries (advert-api).
 * Эндпоинты: списки кампаний, детальная информация о кампаниях.
 * Категория WB API: Продвижение.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WbPromotionApiClient extends AbstractWbApiClient {

    @Override
    protected WbApiCategory getApiCategory() {
        return WbApiCategory.PROMOTION;
    }

    private static final String PROMOTION_COUNT_OPERATION = "количество кампаний";
    private static final String ADVERTS_V2_OPERATION = "детали кампаний (v2)";
    private static final String PROMOTION_FULLSTATS_OPERATION = "статистика кампаний";
    private static final String NORMQUERY_STATS_OPERATION = "статистика поисковых кластеров";
    private static final String CAMPAIGN_START_OPERATION = "запуск кампании";
    private static final String CAMPAIGN_PAUSE_OPERATION = "пауза кампании";
    private static final String BALANCE_OPERATION = "баланс продвижения";
    private static final String BUDGET_OPERATION = "бюджет кампании";
    private static final String BUDGET_DEPOSIT_OPERATION = "пополнение бюджета кампании";
    private static final int ADVERTS_V2_BATCH_SIZE = 50;

    @Value("${wb.retries.max-429-basic}")
    private int maxRetries429Basic;
    @Value("${wb.retries.max-429-personal}")
    private int maxRetries429Personal;

    private final WbApiTokenTypeResolver tokenTypeResolver;

    /**
     * Получение списка кампаний, сгруппированных по типам и статусам.
     * При таймауте или ошибке соединения (в т.ч. UnknownHostException) выполняются ретраи.
     *
     * @param apiKey API ключ продавца
     * @return список кампаний по типам и статусам
     */
    public PromotionCountResponse getPromotionCount(String apiKey) {
        CabinetTokenType tokenType = tokenTypeResolver.resolveByApiKey(apiKey);
        return executeWith429Retry(
                "количество кампаний по типам",
                WbApiEventType.PROMOTION_COUNT.getUri(),
                PROMOTION_COUNT_OPERATION,
                tokenType,
                () -> executeWithConnectionRetry("количество кампаний по типам", () -> getPromotionCountOnce(apiKey)));
    }

    private PromotionCountResponse getPromotionCountOnce(String apiKey) {
        HttpHeaders headers = createAuthHeaders(apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        String url = WbApiEventType.PROMOTION_COUNT.getDefaultUrl();
        logWbApiCall(url, "количество кампаний по типам");

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            validateResponse(response);

            PromotionCountResponse countResponse = objectMapper.readValue(
                    response.getBody(),
                    PromotionCountResponse.class
            );

            log.info("Получено групп кампаний: {}, всего кампаний: {}",
                    countResponse.getAdverts() != null ? countResponse.getAdverts().size() : 0,
                    countResponse.getAll() != null ? countResponse.getAll() : 0);

            return countResponse;

        } catch (HttpClientErrorException e) {
            throwIf401ScopeNotAllowed(e);
            logWbApiError("список кампаний WB", e);
            throw new RestClientException("Ошибка при получении списка кампаний: " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw e;
        } catch (Exception e) {
            logIoErrorOrFull("получении списка кампаний", e);
            throw new RestClientException("Ошибка при получении списка кампаний: " + e.getMessage(), e);
        }
    }

    /**
     * Получение детальной информации о кампаниях (GET /api/advert/v2/adverts).
     * Поддерживаются кампании с единой и ручной ставкой (типы 8 и 9). Максимум 50 ID за запрос.
     * При таймауте или ошибке соединения выполняются ретраи.
     *
     * @param apiKey API ключ продавца
     * @param campaignIds список ID кампаний (рекомендуется не более 50)
     * @return детальная информация о кампаниях в формате PromotionAdvertsResponse
     */
    public PromotionAdvertsResponse getAdvertsV2(String apiKey, List<Long> campaignIds) {
        if (campaignIds == null || campaignIds.isEmpty()) {
            return PromotionAdvertsResponse.builder().adverts(Collections.emptyList()).build();
        }
        CabinetTokenType tokenType = tokenTypeResolver.resolveByApiKey(apiKey);
        return executeWith429Retry(
                "детали кампаний (v2)",
                WbApiEventType.PROMOTION_ADVERTS_BATCH.getUri(),
                ADVERTS_V2_OPERATION,
                tokenType,
                () -> executeWithConnectionRetry("детали кампаний (v2)", () -> getAdvertsV2Once(apiKey, campaignIds)));
    }

    /**
     * Запуск рекламной кампании (GET /adv/v0/start). Допустимые статусы WB: 4, 11.
     *
     * @param apiKey API-ключ кабинета
     * @param advertId ID кампании в WB
     */
    public void startCampaign(String apiKey, long advertId) {
        CabinetTokenType tokenType = tokenTypeResolver.resolveByApiKey(apiKey);
        executeWith429Retry(
                CAMPAIGN_START_OPERATION,
                WbApiEventType.PROMOTION_CAMPAIGN_START.getUri(),
                CAMPAIGN_START_OPERATION,
                tokenType,
                () -> executeWithConnectionRetry(CAMPAIGN_START_OPERATION,
                        () -> invokeCampaignControlOnce(apiKey, advertId, WbApiEventType.PROMOTION_CAMPAIGN_START)));
    }

    /**
     * Пауза рекламной кампании (GET /adv/v0/pause). Допустимый статус WB: 9.
     *
     * @param apiKey API-ключ кабинета
     * @param advertId ID кампании в WB
     */
    public void pauseCampaign(String apiKey, long advertId) {
        CabinetTokenType tokenType = tokenTypeResolver.resolveByApiKey(apiKey);
        executeWith429Retry(
                CAMPAIGN_PAUSE_OPERATION,
                WbApiEventType.PROMOTION_CAMPAIGN_PAUSE.getUri(),
                CAMPAIGN_PAUSE_OPERATION,
                tokenType,
                () -> executeWithConnectionRetry(CAMPAIGN_PAUSE_OPERATION,
                        () -> invokeCampaignControlOnce(apiKey, advertId, WbApiEventType.PROMOTION_CAMPAIGN_PAUSE)));
    }

    private Void invokeCampaignControlOnce(String apiKey, long advertId, WbApiEventType eventType) {
        HttpHeaders headers = createAuthHeaders(apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        String url = UriComponentsBuilder.fromHttpUrl(eventType.getDefaultUrl())
                .queryParam("id", advertId)
                .toUriString();
        logWbApiCall(url, eventType == WbApiEventType.PROMOTION_CAMPAIGN_START ? CAMPAIGN_START_OPERATION : CAMPAIGN_PAUSE_OPERATION);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            // Swagger: 200 «Успешно» без content (08-promotion.yaml — /adv/v0/start, /adv/v0/pause)
            validateResponse(response, true);
            return null;
        } catch (HttpClientErrorException e) {
            throwIf401ScopeNotAllowed(e);
            String userMessage = extractCampaignControlErrorMessage(e);
            logWbApiError(eventType.getUri(), e);
            throw new RestClientException(userMessage != null ? userMessage : e.getMessage(), e);
        } catch (RestClientException e) {
            throw e;
        } catch (Exception e) {
            logIoErrorOrFull(eventType.getUri(), e);
            throw new RestClientException("Ошибка при управлении кампанией: " + e.getMessage(), e);
        }
    }

    private String extractCampaignControlErrorMessage(HttpClientErrorException e) {
        if (PromotionCampaignControlWriteService.isReadOnlyTokenError(e)) {
            return PromotionCampaignControlWriteService.READ_ONLY_USER_MESSAGE;
        }
        String body = e.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            if (e.getStatusCode().value() == 422) {
                return "Статус кампании не изменён";
            }
            return null;
        }
        if (body.contains("\"detail\"")) {
            try {
                WbApiProblemResponse problem = objectMapper.readValue(body, WbApiProblemResponse.class);
                if (problem.getDetail() != null && !problem.getDetail().isBlank()) {
                    return problem.getDetail();
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        try {
            WbApiSimpleErrorResponse simple = objectMapper.readValue(body, WbApiSimpleErrorResponse.class);
            if (simple.getErrorText() != null && !simple.getErrorText().isBlank()) {
                return simple.getErrorText();
            }
        } catch (Exception ignored) {
            // fall through
        }
        if (e.getStatusCode().value() == 422) {
            return body.length() > 200 ? body.substring(0, 200) : body;
        }
        return null;
    }

    private PromotionAdvertsResponse getAdvertsV2Once(String apiKey, List<Long> campaignIds) {
        List<Long> batch = campaignIds.size() > ADVERTS_V2_BATCH_SIZE
                ? campaignIds.subList(0, ADVERTS_V2_BATCH_SIZE)
                : campaignIds;

        HttpHeaders headers = createAuthHeaders(apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(WbApiEventType.PROMOTION_ADVERTS_BATCH.getDefaultUrl())
                .queryParam("ids", batch.stream().map(String::valueOf).collect(Collectors.joining(",")));
        String url = uriBuilder.toUriString();
        logWbApiCall(url, "детали кампаний (v2)");

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );
            validateResponse(response);

            AdvertsV2Response v2Response = objectMapper.readValue(response.getBody(), AdvertsV2Response.class);
            List<PromotionAdvertsResponse.Campaign> campaigns = v2Response.getAdverts() == null
                    ? Collections.emptyList()
                    : v2Response.getAdverts().stream()
                            .map(this::convertV2AdvertToCampaign)
                            .filter(java.util.Objects::nonNull)
                            .collect(Collectors.toList());

            log.info("Получено кампаний: {}", campaigns.size());
            return PromotionAdvertsResponse.builder().adverts(campaigns).build();
        } catch (HttpClientErrorException e) {
            throwIf401ScopeNotAllowed(e);
            logWbApiError("детальная информация о кампаниях WB", e);
            throw new RestClientException("Ошибка при получении детальной информации о кампаниях: " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw e;
        } catch (Exception e) {
            logIoErrorOrFull("получении детальной информации о кампаниях", e);
            throw new RestClientException("Ошибка при получении детальной информации о кампаниях: " + e.getMessage(), e);
        }
    }

    private PromotionAdvertsResponse.Campaign convertV2AdvertToCampaign(AdvertsV2Response.Advert v2) {
        if (v2 == null || v2.getId() == null) return null;
        // bid_type: unified -> type 8 (AUTOMATIC), manual -> type 9 (AUCTION); bidType: unified->2, manual->1
        int type = "unified".equalsIgnoreCase(v2.getBidType()) ? 8 : 9;
        Integer bidTypeCode = "unified".equalsIgnoreCase(v2.getBidType()) ? 2 : 1;
        String name = v2.getSettings() != null ? v2.getSettings().getName() : null;
        AdvertsV2Response.Timestamps ts = v2.getTimestamps();
        List<Long> nmIds = v2.getNmSettings() == null ? new ArrayList<>()
                : v2.getNmSettings().stream()
                        .map(AdvertsV2Response.NmSetting::getNmId)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toList());
        return PromotionAdvertsResponse.Campaign.builder()
                .advertId(v2.getId())
                .name(name)
                .type(type)
                .status(v2.getStatus())
                .bidType(bidTypeCode)
                .startTime(ts != null ? ts.getStarted() : null)
                .endTime(ts != null ? ts.getDeleted() : null)
                .createTime(ts != null ? ts.getCreated() : null)
                .changeTime(ts != null ? ts.getUpdated() : null)
                .nmIds(nmIds)
                .build();
    }

    /**
     * Получение статистики по кампаниям.
     * GET /adv/v3/fullstats — параметры передаются в строке запроса.
     * При таймауте, ошибке соединения или DNS (в т.ч. UnknownHostException) выполняются ретраи.
     *
     * @param apiKey API ключ продавца
     * @param request запрос статистики
     * @return статистика по кампаниям
     */
    public PromotionFullStatsResponse getPromotionFullStats(String apiKey, PromotionFullStatsRequest request) {
        CabinetTokenType tokenType = tokenTypeResolver.resolveByApiKey(apiKey);
        return executeWith429Retry(
                "статистика кампаний за период",
                WbApiEventType.PROMOTION_STATS_BATCH.getUri(),
                PROMOTION_FULLSTATS_OPERATION,
                tokenType,
                () -> executeWithConnectionRetry("статистика кампаний за период", () -> {
            HttpHeaders headers = createAuthHeaders(apiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(WbApiEventType.PROMOTION_STATS_BATCH.getDefaultUrl());
            addQueryParameters(uriBuilder, request);
            String url = uriBuilder.toUriString();
            logWbApiCall(url, "статистика кампаний за период");

            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        entity,
                        String.class
                );

                validateResponse(response);

                List<PromotionFullStatsResponse.CampaignStats> statsList = objectMapper.readValue(
                        response.getBody(),
                        new TypeReference<List<PromotionFullStatsResponse.CampaignStats>>() {}
                );

                PromotionFullStatsResponse statsResponse = PromotionFullStatsResponse.builder()
                        .adverts(statsList)
                        .build();

                int totalDays = statsList != null
                        ? statsList.stream()
                                .mapToInt(campaign -> campaign.getDays() != null ? campaign.getDays().size() : 0)
                                .sum()
                        : 0;

                log.info("Получено кампаний: {}, всего дней статистики: {}",
                        statsList != null ? statsList.size() : 0, totalDays);

                return statsResponse;

            } catch (HttpClientErrorException e) {
                throwIf401ScopeNotAllowed(e);
                logWbApiError("статистика кампаний WB", e);
                throw e;
            } catch (RestClientException e) {
                throw e;
            } catch (Exception e) {
                logIoErrorOrFull("получении статистики кампаний", e);
                throw new RestClientException("Ошибка при получении статистики кампаний: " + e.getMessage(), e);
            }
        }));
    }

    /**
     * Статистика по поисковым кластерам с детализацией по дням.
     * POST /adv/v1/normquery/stats
     */
    public NormQueryStatsResponse postNormQueryStats(String apiKey, NormQueryStatsRequest request) {
        CabinetTokenType tokenType = tokenTypeResolver.resolveByApiKey(apiKey);
        return executeWith429Retry(
                "статистика поисковых кластеров",
                WbApiEventType.PROMOTION_NORMQUERY_STATS_BATCH.getUri(),
                NORMQUERY_STATS_OPERATION,
                tokenType,
                () -> executeWithConnectionRetry("статистика поисковых кластеров", () -> {
                    HttpHeaders headers = createAuthHeaders(apiKey);
                    headers.set("Content-Type", "application/json");
                    HttpEntity<NormQueryStatsRequest> entity = new HttpEntity<>(request, headers);
                    String url = WbApiEventType.PROMOTION_NORMQUERY_STATS_BATCH.getDefaultUrl();
                    logWbApiCall(url, "статистика поисковых кластеров");
                    try {
                        ResponseEntity<String> response = restTemplate.exchange(
                                url,
                                HttpMethod.POST,
                                entity,
                                String.class
                        );
                        validateResponse(response);
                        NormQueryStatsResponse body = objectMapper.readValue(
                                response.getBody(),
                                NormQueryStatsResponse.class
                        );
                        int items = body != null && body.getItems() != null ? body.getItems().size() : 0;
                        log.info("Получено элементов normquery stats: {}", items);
                        return body;
                    } catch (HttpClientErrorException e) {
                        throwIf401ScopeNotAllowed(e);
                        logWbApiError("статистика поисковых кластеров WB", e);
                        throw e;
                    } catch (RestClientException e) {
                        throw e;
                    } catch (Exception e) {
                        logIoErrorOrFull("получении статистики поисковых кластеров", e);
                        throw new RestClientException(
                                "Ошибка при получении статистики поисковых кластеров: " + e.getMessage(), e);
                    }
                }));
    }

    private <T> T executeWith429Retry(
            String context,
            String endpoint,
            String operation,
            CabinetTokenType tokenType,
            Callable<T> attempt
    ) {
        int maxRetries429 = tokenType == CabinetTokenType.PERSONAL ? maxRetries429Personal : maxRetries429Basic;
        for (int retry = 1; retry <= maxRetries429; retry++) {
            try {
                return attempt.call();
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429 && retry < maxRetries429) {
                    log429AndDefer(context, endpoint, operation, retry, maxRetries429, tokenType, e);
                }
                throw e;
            } catch (RestClientException e) {
                if (e.getMessage() != null && e.getMessage().contains("429") && retry < maxRetries429) {
                    log429AndDefer(context, endpoint, operation, retry, maxRetries429, tokenType, null);
                }
                throw e;
            } catch (Exception e) {
                throw new RestClientException("Ошибка при " + context + ": " + e.getMessage(), e);
            }
        }
        throw new RestClientException("Не удалось выполнить " + context + " после " + maxRetries429 + " попыток");
    }

    private void log429AndDefer(
            String context,
            String endpoint,
            String operation,
            int retry,
            int maxRetries429,
            CabinetTokenType tokenType,
            HttpClientErrorException e
    ) {
        log429Metric(endpoint, operation);
        WbApiEventType eventType = resolveEventTypeByEndpoint(endpoint);
        long delayMs = eventType.getRequestDelayMs(tokenType);
        if (e != null) {
            Integer sec = Wb429RateLimitHeadersLogger.parseRetryAfterSeconds(e);
            if (sec != null && sec > 0) {
                delayMs = Math.min((long) sec * 1000L, (long) Integer.MAX_VALUE);
            }
        }
        WbApiEventAttemptContext.AttemptDisplay display =
                WbApiEventAttemptContext.resolveAttemptDisplay(retry, maxRetries429);
        log.warn("WB promotion 429 при {} (попытка {}/{}). Отложенный повтор через {} мс (без sleep).",
                context, display.attempt(), display.maxAttempts(), delayMs);
        throwDeferAfterMillis("WB promotion 429 при " + context, delayMs);
    }

    private WbApiEventType resolveEventTypeByEndpoint(String endpoint) {
        if (WbApiEventType.PROMOTION_STATS_BATCH.getUri().equals(endpoint)) {
            return WbApiEventType.PROMOTION_STATS_BATCH;
        }
        if (WbApiEventType.PROMOTION_NORMQUERY_STATS_BATCH.getUri().equals(endpoint)) {
            return WbApiEventType.PROMOTION_NORMQUERY_STATS_BATCH;
        }
        if (WbApiEventType.PROMOTION_ADVERTS_BATCH.getUri().equals(endpoint)) {
            return WbApiEventType.PROMOTION_ADVERTS_BATCH;
        }
        if (WbApiEventType.PROMOTION_CAMPAIGN_START.getUri().equals(endpoint)) {
            return WbApiEventType.PROMOTION_CAMPAIGN_START;
        }
        if (WbApiEventType.PROMOTION_CAMPAIGN_PAUSE.getUri().equals(endpoint)) {
            return WbApiEventType.PROMOTION_CAMPAIGN_PAUSE;
        }
        return WbApiEventType.PROMOTION_COUNT;
    }

    /**
     * Баланс кабинета продвижения (GET /adv/v1/balance).
     */
    public PromotionBalanceResponse getBalance(String apiKey) {
        CabinetTokenType tokenType = tokenTypeResolver.resolveByApiKey(apiKey);
        return executeWith429Retry(
                BALANCE_OPERATION,
                WbApiEventType.PROMOTION_BALANCE.getUri(),
                BALANCE_OPERATION,
                tokenType,
                () -> executeWithConnectionRetry(BALANCE_OPERATION, () -> getBalanceOnce(apiKey)));
    }

    /**
     * Бюджет кампании (GET /adv/v1/budget).
     */
    public PromotionBudgetResponse getCampaignBudget(String apiKey, long advertId) {
        CabinetTokenType tokenType = tokenTypeResolver.resolveByApiKey(apiKey);
        return executeWith429Retry(
                BUDGET_OPERATION,
                WbApiEventType.PROMOTION_BUDGET_GET.getUri(),
                BUDGET_OPERATION,
                tokenType,
                () -> executeWithConnectionRetry(BUDGET_OPERATION, () -> getCampaignBudgetOnce(apiKey, advertId)));
    }

    /**
     * Пополнение бюджета кампании (POST /adv/v1/budget/deposit).
     * При {@code returnBudget=true} в теле запроса в ответе WB приходит актуальный бюджет.
     */
    public PromotionBudgetResponse depositCampaignBudget(String apiKey, long advertId, PromotionBudgetDepositRequest request) {
        CabinetTokenType tokenType = tokenTypeResolver.resolveByApiKey(apiKey);
        return executeWith429Retry(
                BUDGET_DEPOSIT_OPERATION,
                WbApiEventType.PROMOTION_BUDGET_DEPOSIT.getUri(),
                BUDGET_DEPOSIT_OPERATION,
                tokenType,
                () -> executeWithConnectionRetry(BUDGET_DEPOSIT_OPERATION,
                        () -> depositCampaignBudgetOnce(apiKey, advertId, request)));
    }

    private PromotionBalanceResponse getBalanceOnce(String apiKey) {
        HttpHeaders headers = createAuthHeaders(apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        String url = WbApiEventType.PROMOTION_BALANCE.getDefaultUrl();
        logWbApiCall(url, BALANCE_OPERATION);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            validateResponse(response);
            return objectMapper.readValue(response.getBody(), PromotionBalanceResponse.class);
        } catch (HttpClientErrorException e) {
            throwIf401ScopeNotAllowed(e);
            logWbApiError(BALANCE_OPERATION, e);
            throw new RestClientException("Ошибка при получении баланса: " + e.getMessage(), e);
        } catch (Exception e) {
            logIoErrorOrFull(BALANCE_OPERATION, e);
            throw new RestClientException("Ошибка при получении баланса: " + e.getMessage(), e);
        }
    }

    private PromotionBudgetResponse getCampaignBudgetOnce(String apiKey, long advertId) {
        HttpHeaders headers = createAuthHeaders(apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        String url = UriComponentsBuilder.fromHttpUrl(WbApiEventType.PROMOTION_BUDGET_GET.getDefaultUrl())
                .queryParam("id", advertId)
                .toUriString();
        logWbApiCall(url, BUDGET_OPERATION);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            validateResponse(response);
            return objectMapper.readValue(response.getBody(), PromotionBudgetResponse.class);
        } catch (HttpClientErrorException e) {
            throwIf401ScopeNotAllowed(e);
            logWbApiError(BUDGET_OPERATION, e);
            throw new RestClientException("Ошибка при получении бюджета: " + e.getMessage(), e);
        } catch (Exception e) {
            logIoErrorOrFull(BUDGET_OPERATION, e);
            throw new RestClientException("Ошибка при получении бюджета: " + e.getMessage(), e);
        }
    }

    private PromotionBudgetResponse depositCampaignBudgetOnce(String apiKey, long advertId, PromotionBudgetDepositRequest request) {
        HttpHeaders headers = createAuthHeaders(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<PromotionBudgetDepositRequest> entity = new HttpEntity<>(request, headers);
        String url = UriComponentsBuilder.fromHttpUrl(WbApiEventType.PROMOTION_BUDGET_DEPOSIT.getDefaultUrl())
                .queryParam("id", advertId)
                .toUriString();
        logWbApiCall(url, BUDGET_DEPOSIT_OPERATION);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            validateResponse(response, true);
            if (response.getBody() == null || response.getBody().isBlank()) {
                return null;
            }
            return objectMapper.readValue(response.getBody(), PromotionBudgetResponse.class);
        } catch (HttpClientErrorException e) {
            throwIf401ScopeNotAllowed(e);
            logWbApiError(BUDGET_DEPOSIT_OPERATION, e);
            throw new RestClientException("Ошибка при пополнении бюджета: " + e.getMessage(), e);
        } catch (Exception e) {
            logIoErrorOrFull(BUDGET_DEPOSIT_OPERATION, e);
            throw new RestClientException("Ошибка при пополнении бюджета: " + e.getMessage(), e);
        }
    }

    private void addQueryParameters(UriComponentsBuilder uriBuilder, PromotionFullStatsRequest request) {
        if (request.getAdvertId() != null && !request.getAdvertId().isEmpty()) {
            String idsParam = request.getAdvertId().stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            uriBuilder.queryParam("ids", idsParam);
        }
        uriBuilder.queryParam("beginDate", request.getDateFrom());
        uriBuilder.queryParam("endDate", request.getDateTo());
    }
}

