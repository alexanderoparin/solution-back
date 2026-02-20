package ru.oparin.solution.service.wb;

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
import ru.oparin.solution.dto.wb.FeedbacksResponse;

/**
 * Клиент API отзывов WB (feedbacks-api).
 * Токен должен иметь категорию «Вопросы и отзывы».
 * Категория WB API: Вопросы и отзывы.
 */
@Service
@Slf4j
public class WbFeedbacksApiClient extends AbstractWbApiClient {

    @Override
    protected WbApiCategory getApiCategory() {
        return WbApiCategory.FEEDBACKS_AND_QUESTIONS;
    }

    private static final String FEEDBACKS_ENDPOINT = "/api/v1/feedbacks";
    /** Максимум отзывов в одном ответе по документации. */
    private static final int MAX_TAKE = 5000;
    /** Задержка между запросами из-за лимита 3 req/s. */
    private static final long REQUEST_DELAY_MS = 350;

    @Value("${wb.api.feedbacks-base-url}")
    private String feedbacksBaseUrl;

    /**
     * Получить обработанные отзывы с пагинацией.
     *
     * @param apiKey     API ключ (категория «Вопросы и отзывы»)
     * @param isAnswered true — обработанные, false — необработанные
     * @param nmId      опционально — фильтр по артикулу WB
     * @param take      количество (макс. 5000)
     * @param skip      пропустить
     * @return ответ с data.feedbacks
     */
    public FeedbacksResponse getFeedbacks(String apiKey, boolean isAnswered, Long nmId, int take, int skip) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(feedbacksBaseUrl + FEEDBACKS_ENDPOINT)
                .queryParam("isAnswered", isAnswered)
                .queryParam("take", Math.min(take, MAX_TAKE))
                .queryParam("skip", skip)
                .queryParam("order", "dateDesc");
        if (nmId != null) {
            builder.queryParam("nmId", nmId);
        }
        String url = builder.toUriString();

        HttpHeaders headers = createAuthHeadersWithBearer(apiKey);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        logWbApiCall(url, "отзывы");

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            validateResponse(response);
            return objectMapper.readValue(response.getBody(), FeedbacksResponse.class);
        } catch (HttpClientErrorException e) {
            throwIf401ScopeNotAllowed(e);
            logWbApiError("получение отзывов WB", e);
            throw new RestClientException("Ошибка при получении отзывов: " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при получении отзывов: {}", e.getMessage());
            throw new RestClientException("Ошибка при получении отзывов: " + e.getMessage(), e);
        }
    }

    /**
     * Задержка между запросами (лимит API).
     */
    public static void delayBetweenRequests() {
        try {
            Thread.sleep(REQUEST_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RestClientException("Прервано ожидание перед запросом отзывов", e);
        }
    }
}
