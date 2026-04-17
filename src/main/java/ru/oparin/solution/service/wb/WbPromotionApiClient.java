package ru.oparin.solution.service.wb;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import ru.oparin.solution.dto.wb.*;

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
public class WbPromotionApiClient extends AbstractWbApiClient {

    @Override
    protected WbApiCategory getApiCategory() {
        return WbApiCategory.PROMOTION;
    }

    private static final String PROMOTION_COUNT_ENDPOINT = "/adv/v1/promotion/count";
    private static final String PROMOTION_COUNT_OPERATION = "количество кампаний";
    /** Актуальный эндпоинт деталей кампаний (типы 8 и 9). Устаревшие: /adv/v1/promotion/adverts, /adv/v0/auction/adverts */
    private static final String ADVERTS_V2_ENDPOINT = "/api/advert/v2/adverts";
    private static final String ADVERTS_V2_OPERATION = "детали кампаний (v2)";
    private static final String PROMOTION_FULLSTATS_ENDPOINT = "/adv/v3/fullstats";
    private static final String PROMOTION_FULLSTATS_OPERATION = "статистика кампаний";
    private static final int ADVERTS_V2_BATCH_SIZE = 50;

    @Value("${wb.api.promotion-base-url}")
    private String promotionBaseUrl;
    @Value("${wb.promotion.max-retries-429}")
    private int maxRetries429;
    @Value("${wb.promotion.retry-delay-ms-429}")
    private long retryDelayMs429;

    /**
     * Получение списка кампаний, сгруппированных по типам и статусам.
     * При таймауте или ошибке соединения (в т.ч. UnknownHostException) выполняются ретраи.
     *
     * @param apiKey API ключ продавца
     * @return список кампаний по типам и статусам
     */
    public PromotionCountResponse getPromotionCount(String apiKey) {
        return executeWith429Retry("количество кампаний по типам", PROMOTION_COUNT_ENDPOINT, PROMOTION_COUNT_OPERATION,
                () -> executeWithConnectionRetry("количество кампаний по типам", () -> getPromotionCountOnce(apiKey)));
    }

    private PromotionCountResponse getPromotionCountOnce(String apiKey) {
        HttpHeaders headers = createAuthHeaders(apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        String url = promotionBaseUrl + PROMOTION_COUNT_ENDPOINT;
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
        return executeWith429Retry("детали кампаний (v2)", ADVERTS_V2_ENDPOINT, ADVERTS_V2_OPERATION,
                () -> executeWithConnectionRetry("детали кампаний (v2)", () -> getAdvertsV2Once(apiKey, campaignIds)));
    }

    private PromotionAdvertsResponse getAdvertsV2Once(String apiKey, List<Long> campaignIds) {
        List<Long> batch = campaignIds.size() > ADVERTS_V2_BATCH_SIZE
                ? campaignIds.subList(0, ADVERTS_V2_BATCH_SIZE)
                : campaignIds;

        HttpHeaders headers = createAuthHeaders(apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(promotionBaseUrl + ADVERTS_V2_ENDPOINT)
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
        return executeWith429Retry("статистика кампаний за период", PROMOTION_FULLSTATS_ENDPOINT, PROMOTION_FULLSTATS_OPERATION, () -> executeWithConnectionRetry("статистика кампаний за период", () -> {
            HttpHeaders headers = createAuthHeaders(apiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(promotionBaseUrl + PROMOTION_FULLSTATS_ENDPOINT);
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

    private <T> T executeWith429Retry(String context, String endpoint, String operation, Callable<T> attempt) {
        for (int retry = 1; retry <= maxRetries429; retry++) {
            try {
                return attempt.call();
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429 && retry < maxRetries429) {
                    log429AndSleep(context, endpoint, operation, retry);
                    continue;
                }
                throw e;
            } catch (RestClientException e) {
                if (e.getMessage() != null && e.getMessage().contains("429") && retry < maxRetries429) {
                    log429AndSleep(context, endpoint, operation, retry);
                    continue;
                }
                throw e;
            } catch (Exception e) {
                throw new RestClientException("Ошибка при " + context + ": " + e.getMessage(), e);
            }
        }
        throw new RestClientException("Не удалось выполнить " + context + " после " + maxRetries429 + " попыток");
    }

    private void log429AndSleep(String context, String endpoint, String operation, int retry) {
        log429Metric(endpoint, operation);
        log.warn("WB promotion 429 при {} (попытка {}/{}). Повтор через {} мс...",
                context, retry, maxRetries429, retryDelayMs429);
        sleep(retryDelayMs429);
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

