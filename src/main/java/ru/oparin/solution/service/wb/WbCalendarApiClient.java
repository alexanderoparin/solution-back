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
import ru.oparin.solution.dto.wb.CalendarNomenclaturesResponse;
import ru.oparin.solution.dto.wb.CalendarPromotionsResponse;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Клиент для Календаря акций WB (dp-calendar-api).
 * Токен должен иметь категорию «Цены и скидки».
 * Категория WB API: Цены и скидки.
 */
@Service
@Slf4j
public class WbCalendarApiClient extends AbstractWbApiClient {

    @Override
    protected WbApiCategory getApiCategory() {
        return WbApiCategory.PRICES_AND_DISCOUNTS;
    }

    private static final String PROMOTIONS_ENDPOINT = "/api/v1/calendar/promotions";
    private static final String NOMENCLATURES_ENDPOINT = "/api/v1/calendar/promotions/nomenclatures";
    private static final int PAGE_SIZE = 1000;
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Value("${wb.api.dp-calendar-base-url}")
    private String dpCalendarBaseUrl;

    /**
     * Список акций за период (на одну дату — передать start и end на этот день).
     *
     * @param apiKey     API ключ продавца (категория «Цены и скидки»)
     * @param startDateTime начало периода (ISO-8601, например 2024-01-15T00:00:00Z)
     * @param endDateTime   конец периода (ISO-8601, например 2024-01-15T23:59:59Z)
     * @param allPromo      false — только доступные для участия, true — все
     * @return ответ с полем data.promotions
     */
    public CalendarPromotionsResponse getPromotions(String apiKey, String startDateTime, String endDateTime, boolean allPromo) {
        String url = UriComponentsBuilder.fromHttpUrl(dpCalendarBaseUrl + PROMOTIONS_ENDPOINT)
                .queryParam("startDateTime", startDateTime)
                .queryParam("endDateTime", endDateTime)
                .queryParam("allPromo", allPromo)
                .queryParam("limit", PAGE_SIZE)
                .queryParam("offset", 0)
                .toUriString();

        HttpHeaders headers = createAuthHeadersWithBearer(apiKey);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        logWbApiCall(url, "список акций календаря за период");

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            validateResponse(response);
            return objectMapper.readValue(response.getBody(), CalendarPromotionsResponse.class);
        } catch (HttpClientErrorException e) {
            throwIf401ScopeNotAllowed(e);
            logWbApiError("список акций календаря WB", e);
            throw new RestClientException("Ошибка при получении списка акций календаря: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Ошибка при получении списка акций календаря: {}", e.getMessage());
            throw new RestClientException("Ошибка при получении списка акций календаря: " + e.getMessage(), e);
        }
    }

    /**
     * Список номенклатур по акции (с пагинацией). Собирает все страницы.
     *
     * @param apiKey       API ключ продавца
     * @param promotionId  ID акции
     * @param inAction     true — участвуют в акции, false — подходящие, но не в акции
     * @return список nmId (id из ответа)
     */
    public List<Long> getAllNomenclatureIdsInPromotion(String apiKey, long promotionId, boolean inAction) {
        List<Long> allIds = new ArrayList<>();
        int offset = 0;

        while (true) {
            String url = UriComponentsBuilder.fromHttpUrl(dpCalendarBaseUrl + NOMENCLATURES_ENDPOINT)
                    .queryParam("promotionID", promotionId)
                    .queryParam("inAction", inAction)
                    .queryParam("limit", PAGE_SIZE)
                    .queryParam("offset", offset)
                    .toUriString();

            HttpHeaders headers = createAuthHeadersWithBearer(apiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            logWbApiCall(url, "номенклатуры в акции (promotionId=" + promotionId + ")");

            try {
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
                validateResponse(response);

                CalendarNomenclaturesResponse body = objectMapper.readValue(
                        response.getBody(),
                        CalendarNomenclaturesResponse.class
                );
                if (body.getData() == null || body.getData().getNomenclatures() == null
                        || body.getData().getNomenclatures().isEmpty()) {
                    break;
                }
                for (CalendarNomenclaturesResponse.CalendarNomenclatureItem item : body.getData().getNomenclatures()) {
                    if (item.getId() != null) {
                        allIds.add(item.getId());
                    }
                }
                if (body.getData().getNomenclatures().size() < PAGE_SIZE) {
                    break;
                }
                offset += PAGE_SIZE;
            } catch (HttpClientErrorException e) {
                throwIf401ScopeNotAllowed(e);
                logWbApiError("номенклатуры акции WB (promotionId=" + promotionId + ")", e);
                throw new RestClientException("Ошибка при получении номенклатур акции: " + e.getMessage(), e);
            } catch (Exception e) {
                log.error("Ошибка при получении номенклатур акции {} (offset {}): {}", promotionId, offset, e.getMessage());
                throw new RestClientException("Ошибка при получении номенклатур акции: " + e.getMessage(), e);
            }
        }

        return allIds;
    }

    /**
     * Форматирует начало дня в UTC для WB API.
     */
    public static String startOfDayUtc(ZonedDateTime day) {
        return day.toLocalDate().atStartOfDay(day.getZone()).format(ISO_OFFSET);
    }

    /**
     * Форматирует конец дня в UTC для WB API (23:59:59).
     */
    public static String endOfDayUtc(ZonedDateTime day) {
        return day.toLocalDate().atTime(23, 59, 59).atZone(day.getZone()).format(ISO_OFFSET);
    }
}
