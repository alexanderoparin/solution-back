package ru.oparin.solution.service.ozon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import ru.oparin.solution.dto.ozon.*;
import ru.oparin.solution.model.OzonApiBaseUrl;
import ru.oparin.solution.model.OzonApiEventType;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Клиент Ozon Performance API: OAuth-токен и список рекламных кампаний.
 */
@Service
@Slf4j
public class OzonPerformanceApiClient {

    private static final String TOKEN_URL = OzonApiEventType.PERFORMANCE_TOKEN.getDefaultUrl();
    private static final String CAMPAIGNS_URL = OzonApiEventType.CAMPAIGNS_CABINET.getDefaultUrl();
    private static final String DAILY_STATS_URL = OzonApiEventType.CAMPAIGN_STATS_CABINET.getDefaultUrl();
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int TOKEN_REFRESH_MARGIN_SECONDS = 60;
    private static final int MAX_BODY_LOG_LENGTH = 2000;
    /** Сколько campaignIds передаём в один daily-запрос. */
    private static final int DAILY_STATS_CAMPAIGN_BATCH = 50;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<Long, CachedToken> tokenCache = new ConcurrentHashMap<>();

    public OzonPerformanceApiClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restTemplate = new RestTemplate(requestFactory);
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Запрашивает access_token по client_id и client_secret (без кэша).
     */
    public OzonPerformanceTokenResponse requestToken(String clientId, String clientSecret) {
        OzonPerformanceTokenRequest body = OzonPerformanceTokenRequest.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<OzonPerformanceTokenRequest> entity = new HttpEntity<>(body, headers);

        log.info("Ozon Performance token: POST {}, client_id={}", TOKEN_URL, clientId);
        long startedAtMs = System.currentTimeMillis();
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    TOKEN_URL,
                    HttpMethod.POST,
                    entity,
                    String.class
            );
            long elapsedMs = System.currentTimeMillis() - startedAtMs;
            String responseBody = response.getBody();
            log.info("Ozon Performance token: HTTP {} {} ms", response.getStatusCode().value(), elapsedMs);
            if (!response.getStatusCode().is2xxSuccessful() || responseBody == null || responseBody.isBlank()) {
                throw new RestClientException("Неожиданный ответ Performance token: " + response.getStatusCode());
            }
            OzonPerformanceTokenResponse token = objectMapper.readValue(responseBody, OzonPerformanceTokenResponse.class);
            if (token.getAccessToken() == null || token.getAccessToken().isBlank()) {
                throw new RestClientException("Performance API не вернул access_token");
            }
            return token;
        } catch (HttpClientErrorException e) {
            long elapsedMs = System.currentTimeMillis() - startedAtMs;
            log.warn("Ozon Performance token: HTTP {} {}, {} ms, тело: {}",
                    e.getStatusCode().value(), e.getStatusText(), elapsedMs,
                    truncateForLog(e.getResponseBodyAsString()));
            throw e;
        } catch (RestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new RestClientException("Ошибка запроса Performance token: " + e.getMessage(), e);
        }
    }

    /**
     * Возвращает Bearer-токен для кабинета с кэшированием до истечения срока.
     */
    public String getAccessToken(Long cabinetId, String clientId, String clientSecret) {
        CachedToken cached = tokenCache.get(cabinetId);
        if (cached != null && cached.isValid()) {
            return cached.accessToken();
        }
        OzonPerformanceTokenResponse token = requestToken(clientId, clientSecret);
        long expiresIn = token.getExpiresIn() != null ? token.getExpiresIn() : 3600L;
        Instant expiresAt = Instant.now().plusSeconds(Math.max(60, expiresIn - TOKEN_REFRESH_MARGIN_SECONDS));
        tokenCache.put(cabinetId, new CachedToken(token.getAccessToken(), expiresAt));
        return token.getAccessToken();
    }

    /**
     * Сбрасывает кэш токена кабинета (после смены credentials).
     */
    public void invalidateTokenCache(Long cabinetId) {
        if (cabinetId != null) {
            tokenCache.remove(cabinetId);
        }
    }

    /**
     * Одна страница списка кампаний.
     */
    public OzonPerformanceCampaignListResponse listCampaigns(
            String accessToken,
            int page,
            int pageSize
    ) {
        String url = CAMPAIGNS_URL + "?page=" + page + "&pageSize=" + pageSize;
        HttpHeaders headers = bearerHeaders(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        log.info("Ozon Performance campaigns: GET {}, page={}", CAMPAIGNS_URL, page);
        long startedAtMs = System.currentTimeMillis();
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            long elapsedMs = System.currentTimeMillis() - startedAtMs;
            String body = response.getBody();
            log.info("Ozon Performance campaigns: HTTP {} {} ms", response.getStatusCode().value(), elapsedMs);
            if (!response.getStatusCode().is2xxSuccessful() || body == null || body.isBlank()) {
                throw new RestClientException("Неожиданный ответ campaigns: " + response.getStatusCode());
            }
            return objectMapper.readValue(body, OzonPerformanceCampaignListResponse.class);
        } catch (HttpClientErrorException e) {
            long elapsedMs = System.currentTimeMillis() - startedAtMs;
            log.warn("Ozon Performance campaigns: HTTP {} {}, {} ms, тело: {}",
                    e.getStatusCode().value(), e.getStatusText(), elapsedMs,
                    truncateForLog(e.getResponseBodyAsString()));
            throw e;
        } catch (RestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new RestClientException("Ошибка запроса campaigns: " + e.getMessage(), e);
        }
    }

    /**
     * Собирает все страницы списка кампаний.
     */
    public java.util.List<OzonPerformanceCampaignListResponse.Item> listAllCampaigns(
            Long cabinetId,
            String clientId,
            String clientSecret
    ) {
        String accessToken = getAccessToken(cabinetId, clientId, clientSecret);
        java.util.List<OzonPerformanceCampaignListResponse.Item> all = new java.util.ArrayList<>();
        int page = 1;
        while (true) {
            OzonPerformanceCampaignListResponse response = listCampaigns(accessToken, page, DEFAULT_PAGE_SIZE);
            java.util.List<OzonPerformanceCampaignListResponse.Item> items = response.resolveItems();
            if (items.isEmpty()) {
                break;
            }
            all.addAll(items);
            if (items.size() < DEFAULT_PAGE_SIZE) {
                break;
            }
            page++;
            if (page > 500) {
                log.warn("Ozon Performance campaigns: достигнут лимит страниц для cabinetId={}", cabinetId);
                break;
            }
        }
        return all;
    }

    /**
     * Дневная статистика по всем кампаниям кабинета за период (батчами по campaignIds).
     */
    public List<OzonPerformanceDailyStatsResponse.Row> getDailyStatistics(
            Long cabinetId,
            String clientId,
            String clientSecret,
            Collection<Long> campaignIds,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        String accessToken = getAccessToken(cabinetId, clientId, clientSecret);
        if (campaignIds == null || campaignIds.isEmpty()) {
            return getDailyStatisticsBatch(accessToken, List.of(), dateFrom, dateTo);
        }
        List<Long> ids = campaignIds.stream().filter(id -> id != null).distinct().toList();
        List<OzonPerformanceDailyStatsResponse.Row> all = new java.util.ArrayList<>();
        for (int i = 0; i < ids.size(); i += DAILY_STATS_CAMPAIGN_BATCH) {
            List<Long> batch = ids.subList(i, Math.min(i + DAILY_STATS_CAMPAIGN_BATCH, ids.size()));
            all.addAll(getDailyStatisticsBatch(accessToken, batch, dateFrom, dateTo));
        }
        return all;
    }

    private List<OzonPerformanceDailyStatsResponse.Row> getDailyStatisticsBatch(
            String accessToken,
            List<Long> campaignIds,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(DAILY_STATS_URL)
                .queryParam("dateFrom", dateFrom.toString())
                .queryParam("dateTo", dateTo.toString());
        for (Long id : campaignIds) {
            // Официальный пример использует campaigns=; в схеме — campaignIds. Шлём оба.
            builder.queryParam("campaigns", id);
            builder.queryParam("campaignIds", id);
        }
        String url = builder.toUriString();
        HttpHeaders headers = bearerHeaders(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        log.info("Ozon Performance daily stats: GET {}, campaigns={}, period={}..{}",
                DAILY_STATS_URL, campaignIds.size(), dateFrom, dateTo);
        long startedAtMs = System.currentTimeMillis();
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            long elapsedMs = System.currentTimeMillis() - startedAtMs;
            String body = response.getBody();
            log.info("Ozon Performance daily stats: HTTP {} {} ms, body={}",
                    response.getStatusCode().value(), elapsedMs, truncateForLog(body));
            if (!response.getStatusCode().is2xxSuccessful() || body == null || body.isBlank()) {
                throw new RestClientException("Неожиданный ответ daily stats: " + response.getStatusCode());
            }
            JsonNode root = objectMapper.readTree(body);
            return OzonPerformanceDailyStatsResponse.parseRows(root);
        } catch (HttpClientErrorException e) {
            long elapsedMs = System.currentTimeMillis() - startedAtMs;
            log.warn("Ozon Performance daily stats: HTTP {} {}, {} ms, тело: {}",
                    e.getStatusCode().value(), e.getStatusText(), elapsedMs,
                    truncateForLog(e.getResponseBodyAsString()));
            throw e;
        } catch (RestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new RestClientException("Ошибка запроса daily stats: " + e.getMessage(), e);
        }
    }

    /**
     * SKU кампании: сначала {@code /v2/products}, при пустом ответе — {@code /objects}.
     */
    public List<Long> listCampaignSkus(Long cabinetId, String clientId, String clientSecret, Long campaignId) {
        String accessToken = getAccessToken(cabinetId, clientId, clientSecret);
        String base = OzonApiBaseUrl.PERFORMANCE.getDefaultBaseUrl() + "/api/client/campaign/" + campaignId;
        List<Long> fromProducts = fetchCampaignSkuList(accessToken, base + "/v2/products");
        if (!fromProducts.isEmpty()) {
            return fromProducts;
        }
        return fetchCampaignSkuList(accessToken, base + "/objects");
    }

    private List<Long> fetchCampaignSkuList(String accessToken, String url) {
        HttpHeaders headers = bearerHeaders(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        log.info("Ozon Performance campaign objects: GET {}", url);
        long startedAtMs = System.currentTimeMillis();
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            long elapsedMs = System.currentTimeMillis() - startedAtMs;
            String body = response.getBody();
            log.info("Ozon Performance campaign objects: HTTP {} {} ms", response.getStatusCode().value(), elapsedMs);
            if (!response.getStatusCode().is2xxSuccessful() || body == null || body.isBlank()) {
                return List.of();
            }
            return OzonPerformanceCampaignObjectsResponse.parseSkus(objectMapper.readTree(body));
        } catch (HttpClientErrorException e) {
            long elapsedMs = System.currentTimeMillis() - startedAtMs;
            if (e.getStatusCode().value() == 404) {
                log.debug("Ozon Performance campaign objects: 404 for {}, {} ms", url, elapsedMs);
                return List.of();
            }
            log.warn("Ozon Performance campaign objects: HTTP {} {}, {} ms, тело: {}",
                    e.getStatusCode().value(), e.getStatusText(), elapsedMs,
                    truncateForLog(e.getResponseBodyAsString()));
            throw e;
        } catch (RestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new RestClientException("Ошибка запроса campaign objects: " + e.getMessage(), e);
        }
    }

    private static HttpHeaders bearerHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        return headers;
    }

    private static String truncateForLog(String body) {
        if (body == null || body.isBlank()) {
            return "(пусто)";
        }
        if (body.length() <= MAX_BODY_LOG_LENGTH) {
            return body;
        }
        return body.substring(0, MAX_BODY_LOG_LENGTH) + "...";
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
        boolean isValid() {
            return accessToken != null && !accessToken.isBlank() && Instant.now().isBefore(expiresAt);
        }
    }
}
