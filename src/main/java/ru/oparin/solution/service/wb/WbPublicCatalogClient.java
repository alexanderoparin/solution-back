package ru.oparin.solution.service.wb;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import ru.oparin.solution.dto.wb.WbPublicCardDetailResponse;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Публичный catalog API WB: рейтинг карточки как на странице товара ({@code reviewRating}).
 * Seller-токен не требуется.
 */
@Component
@Slf4j
public class WbPublicCatalogClient {

    private static final String DETAIL_PATH = "/cards/v2/detail";
    private static final List<String> BASE_URLS = List.of(
            "https://card.wb.ru",
            "https://u-card.wb.ru"
    );
    private static final int DEST = -1257786;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public WbPublicCatalogClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restTemplate = new RestTemplate(requestFactory);
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Загружает {@code reviewRating} по списку nmId одним запросом.
     *
     * @param nmIds артикулы WB (рекомендуется до 100 за вызов)
     * @return nmId → reviewRating (только найденные товары)
     */
    public Map<Long, Double> fetchReviewRatings(Collection<Long> nmIds) {
        List<Long> ids = nmIds == null
                ? List.of()
                : nmIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }

        String nmParam = ids.stream().map(String::valueOf).collect(Collectors.joining(";"));
        Exception lastError = null;
        for (String baseUrl : BASE_URLS) {
            String url = UriComponentsBuilder
                    .fromUriString(baseUrl + DETAIL_PATH)
                    .queryParam("appType", 1)
                    .queryParam("curr", "rub")
                    .queryParam("dest", DEST)
                    .queryParam("nm", nmParam)
                    .build(true)
                    .toUriString();
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
                headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; ClickiBot/1.0)");
                ResponseEntity<String> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        String.class
                );
                String body = response.getBody();
                if (body == null || body.isBlank()) {
                    throw new RestClientException("Пустой ответ публичного catalog API");
                }
                WbPublicCardDetailResponse parsed = objectMapper.readValue(body, WbPublicCardDetailResponse.class);
                return toRatingMap(parsed);
            } catch (Exception e) {
                lastError = e;
                log.warn("Публичный catalog API недоступен (baseUrl={}): {}", baseUrl, e.getMessage());
            }
        }
        throw new RestClientException(
                "Не удалось получить reviewRating из публичного catalog API: "
                        + (lastError != null ? lastError.getMessage() : "unknown"),
                lastError
        );
    }

    private static Map<Long, Double> toRatingMap(WbPublicCardDetailResponse response) {
        if (response == null || response.getData() == null || response.getData().getProducts() == null) {
            return Map.of();
        }
        Map<Long, Double> result = new HashMap<>();
        for (WbPublicCardDetailResponse.Product product : response.getData().getProducts()) {
            if (product == null || product.getId() == null) {
                continue;
            }
            Double rating = product.resolveReviewRating();
            if (rating != null) {
                result.put(product.getId(), rating);
            }
        }
        return result;
    }
}
