package ru.oparin.solution.service.ozon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

import java.nio.charset.StandardCharsets;
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
    private static final String STATISTICS_SUBMIT_URL =
            OzonApiBaseUrl.PERFORMANCE.getDefaultBaseUrl() + "/api/client/statistics";
    private static final String SEARCH_PHRASES_SUBMIT_URL =
            OzonApiBaseUrl.PERFORMANCE.getDefaultBaseUrl() + "/api/client/statistics/phrases/json";
    private static final String STATISTICS_REPORT_URL =
            OzonApiBaseUrl.PERFORMANCE.getDefaultBaseUrl() + "/api/client/statistics/report";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int TOKEN_REFRESH_MARGIN_SECONDS = 60;
    private static final int MAX_BODY_LOG_LENGTH = 2000;
    /** Сколько campaignIds передаём в один daily-запрос. */
    private static final int DAILY_STATS_CAMPAIGN_BATCH = 50;
    /** Сколько campaignIds в одном async product-stats отчёте. */
    private static final int PRODUCT_STATS_CAMPAIGN_BATCH = 20;
    /** По одной кампании в search-phrases отчёте (CSV, без ZIP). */
    private static final int SEARCH_PHRASES_CAMPAIGN_BATCH = 1;
    /** Интервал опроса async-отчёта внутри одного execute. */
    private static final int PRODUCT_STATS_POLL_ATTEMPTS = 24;
    private static final long PRODUCT_STATS_POLL_SLEEP_MS = 3_000L;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<Long, CachedToken> tokenCache = new ConcurrentHashMap<>();

    public OzonPerformanceApiClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restTemplate = new RestTemplate(requestFactory);
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
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
     * Запрашивает async-отчёт статистики по SKU в кампаниях (POST /api/client/statistics, groupBy=DATE).
     */
    public String submitProductStatisticsReport(
            Long cabinetId,
            String clientId,
            String clientSecret,
            Collection<Long> campaignIds,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        String accessToken = getAccessToken(cabinetId, clientId, clientSecret);
        List<Long> ids = campaignIds == null ? List.of() : campaignIds.stream().filter(id -> id != null).distinct().toList();
        OzonPerformanceStatisticsSubmitRequest body = OzonPerformanceStatisticsSubmitRequest.builder()
                .campaigns(ids)
                .dateFrom(dateFrom.toString())
                .dateTo(dateTo.toString())
                .groupBy("DATE")
                .build();
        HttpHeaders headers = bearerHeaders(accessToken);
        HttpEntity<OzonPerformanceStatisticsSubmitRequest> entity = new HttpEntity<>(body, headers);
        log.info("Ozon Performance product stats submit: POST {}, campaigns={}, period={}..{}",
                STATISTICS_SUBMIT_URL, ids.size(), dateFrom, dateTo);
        long startedAtMs = System.currentTimeMillis();
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    STATISTICS_SUBMIT_URL, HttpMethod.POST, entity, String.class);
            long elapsedMs = System.currentTimeMillis() - startedAtMs;
            String responseBody = response.getBody();
            log.info("Ozon Performance product stats submit: HTTP {} {} ms",
                    response.getStatusCode().value(), elapsedMs);
            if (!response.getStatusCode().is2xxSuccessful() || responseBody == null || responseBody.isBlank()) {
                throw new RestClientException("Неожиданный ответ submit statistics: " + response.getStatusCode());
            }
            OzonPerformanceStatisticsSubmitResponse parsed =
                    objectMapper.readValue(responseBody, OzonPerformanceStatisticsSubmitResponse.class);
            if (parsed.getUuid() == null || parsed.getUuid().isBlank()) {
                throw new RestClientException("Performance API не вернул UUID отчёта");
            }
            return parsed.getUuid();
        } catch (HttpClientErrorException e) {
            long elapsedMs = System.currentTimeMillis() - startedAtMs;
            log.warn("Ozon Performance product stats submit: HTTP {} {}, {} ms, тело: {}",
                    e.getStatusCode().value(), e.getStatusText(), elapsedMs,
                    truncateForLog(e.getResponseBodyAsString()));
            throw e;
        } catch (RestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new RestClientException("Ошибка submit statistics: " + e.getMessage(), e);
        }
    }

    /**
     * Запрашивает async-отчёт по поисковым запросам (POST /api/client/statistics/phrases/json).
     */
    public String submitSearchPhrasesReport(
            Long cabinetId,
            String clientId,
            String clientSecret,
            Collection<Long> campaignIds,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        String accessToken = getAccessToken(cabinetId, clientId, clientSecret);
        List<Long> ids = campaignIds == null ? List.of() : campaignIds.stream().filter(id -> id != null).distinct().toList();
        OzonPerformanceStatisticsSubmitRequest body = OzonPerformanceStatisticsSubmitRequest.builder()
                .campaigns(ids)
                .dateFrom(dateFrom.toString())
                .dateTo(dateTo.toString())
                .groupBy("DATE")
                .build();
        HttpHeaders headers = bearerHeaders(accessToken);
        HttpEntity<OzonPerformanceStatisticsSubmitRequest> entity = new HttpEntity<>(body, headers);
        log.info("Ozon Performance search phrases submit: POST {}, campaigns={}, period={}..{}",
                SEARCH_PHRASES_SUBMIT_URL, ids.size(), dateFrom, dateTo);
        long startedAtMs = System.currentTimeMillis();
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    SEARCH_PHRASES_SUBMIT_URL, HttpMethod.POST, entity, String.class);
            long elapsedMs = System.currentTimeMillis() - startedAtMs;
            String responseBody = response.getBody();
            log.info("Ozon Performance search phrases submit: HTTP {} {} ms",
                    response.getStatusCode().value(), elapsedMs);
            if (!response.getStatusCode().is2xxSuccessful() || responseBody == null || responseBody.isBlank()) {
                throw new RestClientException("Неожиданный ответ submit search phrases: " + response.getStatusCode());
            }
            OzonPerformanceStatisticsSubmitResponse parsed =
                    objectMapper.readValue(responseBody, OzonPerformanceStatisticsSubmitResponse.class);
            if (parsed.getUuid() == null || parsed.getUuid().isBlank()) {
                throw new RestClientException("Performance API не вернул UUID отчёта search phrases");
            }
            return parsed.getUuid();
        } catch (HttpClientErrorException e) {
            long elapsedMs = System.currentTimeMillis() - startedAtMs;
            log.warn("Ozon Performance search phrases submit: HTTP {} {}, {} ms, тело: {}",
                    e.getStatusCode().value(), e.getStatusText(), elapsedMs,
                    truncateForLog(e.getResponseBodyAsString()));
            throw e;
        } catch (RestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new RestClientException("Ошибка submit search phrases: " + e.getMessage(), e);
        }
    }

    /**
     * Скачивает async-отчёт search phrases и разбирает строки (JSON или CSV).
     */
    public List<OzonPerformanceSearchPhrasesResponse.Row> downloadSearchPhrasesReport(
            Long cabinetId,
            String clientId,
            String clientSecret,
            String reportUuid,
            Long fallbackCampaignId
    ) {
        String accessToken = getAccessToken(cabinetId, clientId, clientSecret);
        String url = STATISTICS_REPORT_URL + "?UUID=" + reportUuid;
        HttpHeaders headers = bearerHeaders(accessToken);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.ALL));
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        log.info("Ozon Performance search phrases download: GET {}, uuid={}", STATISTICS_REPORT_URL, reportUuid);
        long startedAtMs = System.currentTimeMillis();
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);
            long elapsedMs = System.currentTimeMillis() - startedAtMs;
            byte[] bodyBytes = response.getBody();
            log.info("Ozon Performance search phrases download: HTTP {} {} ms, bytes={}",
                    response.getStatusCode().value(), elapsedMs, bodyBytes != null ? bodyBytes.length : 0);
            if (!response.getStatusCode().is2xxSuccessful() || bodyBytes == null || bodyBytes.length == 0) {
                throw new RestClientException("Неожиданный ответ search phrases download: " + response.getStatusCode());
            }
            if (bodyBytes.length >= 2 && bodyBytes[0] == 'P' && bodyBytes[1] == 'K') {
                log.warn("Ozon search phrases report uuid={} вернул ZIP — пропуск (ожидался CSV/JSON)", reportUuid);
                return List.of();
            }
            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            String trimmed = body.trim();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                JsonNode root = objectMapper.readTree(body);
                List<OzonPerformanceSearchPhrasesResponse.Row> rows = OzonPerformanceSearchPhrasesResponse.parseRows(root);
                rows.forEach(row -> {
                    if (row.getCampaignId() == null) {
                        row.setCampaignId(fallbackCampaignId);
                    }
                });
                return rows;
            }
            return OzonPerformanceSearchPhrasesCsvParser.parse(body, fallbackCampaignId);
        } catch (HttpClientErrorException e) {
            long elapsedMs = System.currentTimeMillis() - startedAtMs;
            log.warn("Ozon Performance search phrases download: HTTP {} {}, {} ms, тело: {}",
                    e.getStatusCode().value(), e.getStatusText(), elapsedMs,
                    truncateForLog(e.getResponseBodyAsString()));
            throw e;
        } catch (RestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new RestClientException("Ошибка search phrases download: " + e.getMessage(), e);
        }
    }

    public int getSearchPhrasesCampaignBatchSize() {
        return SEARCH_PHRASES_CAMPAIGN_BATCH;
    }

    /**
     * Статус async-отчёта (GET /api/client/statistics/{UUID}).
     */
    public OzonPerformanceReportStatusResponse getStatisticsReportStatus(
            Long cabinetId,
            String clientId,
            String clientSecret,
            String reportUuid
    ) {
        String accessToken = getAccessToken(cabinetId, clientId, clientSecret);
        String url = OzonApiBaseUrl.PERFORMANCE.getDefaultBaseUrl() + "/api/client/statistics/" + reportUuid;
        HttpHeaders headers = bearerHeaders(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        long startedAtMs = System.currentTimeMillis();
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            long elapsedMs = System.currentTimeMillis() - startedAtMs;
            String body = response.getBody();
            log.info("Ozon Performance report status: HTTP {} {} ms, uuid={}",
                    response.getStatusCode().value(), elapsedMs, reportUuid);
            if (!response.getStatusCode().is2xxSuccessful() || body == null || body.isBlank()) {
                throw new RestClientException("Неожиданный ответ report status: " + response.getStatusCode());
            }
            return objectMapper.readValue(body, OzonPerformanceReportStatusResponse.class);
        } catch (HttpClientErrorException e) {
            long elapsedMs = System.currentTimeMillis() - startedAtMs;
            log.warn("Ozon Performance report status: HTTP {} {}, {} ms, тело: {}",
                    e.getStatusCode().value(), e.getStatusText(), elapsedMs,
                    truncateForLog(e.getResponseBodyAsString()));
            throw e;
        } catch (RestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new RestClientException("Ошибка report status: " + e.getMessage(), e);
        }
    }

    /**
     * Скачивает async-отчёт и разбирает строки по SKU (JSON или CSV).
     */
    public List<OzonPerformanceProductStatsResponse.Row> downloadProductStatisticsReport(
            Long cabinetId,
            String clientId,
            String clientSecret,
            String reportUuid,
            Long fallbackCampaignId
    ) {
        String accessToken = getAccessToken(cabinetId, clientId, clientSecret);
        String url = STATISTICS_REPORT_URL + "?UUID=" + reportUuid;
        HttpHeaders headers = bearerHeaders(accessToken);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.ALL));
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        log.info("Ozon Performance product stats download: GET {}, uuid={}", STATISTICS_REPORT_URL, reportUuid);
        long startedAtMs = System.currentTimeMillis();
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);
            long elapsedMs = System.currentTimeMillis() - startedAtMs;
            byte[] bodyBytes = response.getBody();
            log.info("Ozon Performance product stats download: HTTP {} {} ms, bytes={}",
                    response.getStatusCode().value(), elapsedMs, bodyBytes != null ? bodyBytes.length : 0);
            if (!response.getStatusCode().is2xxSuccessful() || bodyBytes == null || bodyBytes.length == 0) {
                throw new RestClientException("Неожиданный ответ report download: " + response.getStatusCode());
            }
            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            String trimmed = body.trim();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                JsonNode root = objectMapper.readTree(body);
                return OzonPerformanceProductStatsResponse.parseRows(root);
            }
            return OzonPerformanceProductStatsCsvParser.parse(body, fallbackCampaignId);
        } catch (HttpClientErrorException e) {
            long elapsedMs = System.currentTimeMillis() - startedAtMs;
            log.warn("Ozon Performance product stats download: HTTP {} {}, {} ms, тело: {}",
                    e.getStatusCode().value(), e.getStatusText(), elapsedMs,
                    truncateForLog(e.getResponseBodyAsString()));
            throw e;
        } catch (RestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new RestClientException("Ошибка report download: " + e.getMessage(), e);
        }
    }

    /**
     * Ожидает готовности async-отчёта внутри одного вызова (короткий poll-loop).
     */
    public OzonPerformanceReportStatusResponse waitForReportReady(
            Long cabinetId,
            String clientId,
            String clientSecret,
            String reportUuid
    ) throws InterruptedException {
        OzonPerformanceReportStatusResponse last = null;
        for (int attempt = 0; attempt < PRODUCT_STATS_POLL_ATTEMPTS; attempt++) {
            last = getStatisticsReportStatus(cabinetId, clientId, clientSecret, reportUuid);
            String state = last.getState() != null ? last.getState().trim().toUpperCase() : "";
            if ("OK".equals(state)) {
                return last;
            }
            if ("ERROR".equals(state)) {
                return last;
            }
            if (attempt < PRODUCT_STATS_POLL_ATTEMPTS - 1) {
                Thread.sleep(PRODUCT_STATS_POLL_SLEEP_MS);
            }
        }
        return last;
    }

    public int getProductStatsCampaignBatchSize() {
        return PRODUCT_STATS_CAMPAIGN_BATCH;
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

    /**
     * Активирует кампанию (POST .../activate).
     */
    public void activateCampaign(Long cabinetId, String clientId, String clientSecret, Long campaignId) {
        postCampaignAction(cabinetId, clientId, clientSecret, campaignId, "activate");
    }

    /**
     * Выключает кампанию (POST .../deactivate).
     */
    public void deactivateCampaign(Long cabinetId, String clientId, String clientSecret, Long campaignId) {
        postCampaignAction(cabinetId, clientId, clientSecret, campaignId, "deactivate");
    }

    private void postCampaignAction(
            Long cabinetId,
            String clientId,
            String clientSecret,
            Long campaignId,
            String action
    ) {
        String accessToken = getAccessToken(cabinetId, clientId, clientSecret);
        String url = OzonApiBaseUrl.PERFORMANCE.getDefaultBaseUrl()
                + "/api/client/campaign/" + campaignId + "/" + action;
        HttpHeaders headers = bearerHeaders(accessToken);
        HttpEntity<String> entity = new HttpEntity<>("{}", headers);
        log.info("Ozon Performance campaign {}: POST {}", action, url);
        long startedAtMs = System.currentTimeMillis();
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            long elapsedMs = System.currentTimeMillis() - startedAtMs;
            log.info("Ozon Performance campaign {}: HTTP {} {} ms",
                    action, response.getStatusCode().value(), elapsedMs);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RestClientException("Ozon Performance " + action + ": HTTP " + response.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            long elapsedMs = System.currentTimeMillis() - startedAtMs;
            log.warn("Ozon Performance campaign {}: HTTP {} {}, {} ms, тело: {}",
                    action, e.getStatusCode().value(), e.getStatusText(), elapsedMs,
                    truncateForLog(e.getResponseBodyAsString()));
            throw e;
        } catch (RestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new RestClientException("Ошибка Ozon Performance " + action + ": " + e.getMessage(), e);
        }
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
