package ru.oparin.solution.service.wb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import com.fasterxml.jackson.core.type.TypeReference;
import ru.oparin.solution.dto.wb.AuctionAdvertsResponse;
import ru.oparin.solution.dto.wb.PromotionAdvertsResponse;
import ru.oparin.solution.dto.wb.PromotionCountResponse;
import ru.oparin.solution.dto.wb.PromotionFullStatsRequest;
import ru.oparin.solution.dto.wb.PromotionFullStatsResponse;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Клиент для работы с Promotion API Wildberries.
 * Эндпоинты: списки кампаний, детальная информация о кампаниях.
 */
@Service
@Slf4j
public class WbPromotionApiClient extends AbstractWbApiClient {

    private static final String PROMOTION_COUNT_ENDPOINT = "/adv/v1/promotion/count";
    private static final String PROMOTION_ADVERTS_ENDPOINT = "/adv/v1/promotion/adverts";
    private static final String AUCTION_ADVERTS_ENDPOINT = "/adv/v0/auction/adverts";
    private static final String PROMOTION_FULLSTATS_ENDPOINT = "/adv/v3/fullstats";

    @Value("${wb.api.promotion-base-url}")
    private String promotionBaseUrl;

    /**
     * Получение списка кампаний, сгруппированных по типам и статусам.
     *
     * @param apiKey API ключ продавца
     * @return список кампаний по типам и статусам
     */
    public PromotionCountResponse getPromotionCount(String apiKey) {
        HttpHeaders headers = createAuthHeaders(apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        String url = promotionBaseUrl + PROMOTION_COUNT_ENDPOINT;

        log.info("Запрос списка кампаний: {}", url);

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

        } catch (Exception e) {
            log.error("Ошибка при получении списка кампаний: {}", e.getMessage(), e);
            throw new RestClientException("Ошибка при получении списка кампаний: " + e.getMessage(), e);
        }
    }

    /**
     * Получение детальной информации о кампаниях.
     *
     * @param apiKey API ключ продавца
     * @param campaignIds список ID кампаний
     * @return детальная информация о кампаниях
     */
    public PromotionAdvertsResponse getPromotionAdverts(String apiKey, List<Long> campaignIds) {
        HttpHeaders headers = createAuthHeaders(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // API ожидает массив ID напрямую, а не объект с полем id
        HttpEntity<List<Long>> entity = new HttpEntity<>(campaignIds, headers);
        String url = promotionBaseUrl + PROMOTION_ADVERTS_ENDPOINT;

        log.info("Запрос детальной информации о кампаниях: {}", url);
        log.info("Количество кампаний в запросе: {}", campaignIds != null ? campaignIds.size() : 0);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            validateResponse(response);

            // API возвращает массив кампаний напрямую, а не объект с полем adverts
            List<PromotionAdvertsResponse.Campaign> campaigns = objectMapper.readValue(
                    response.getBody(),
                    objectMapper.getTypeFactory().constructCollectionType(
                            List.class,
                            PromotionAdvertsResponse.Campaign.class
                    )
            );

            PromotionAdvertsResponse advertsResponse = PromotionAdvertsResponse.builder()
                    .adverts(campaigns)
                    .build();

            log.info("Получено кампаний: {}", 
                    campaigns != null ? campaigns.size() : 0);

            return advertsResponse;

        } catch (Exception e) {
            log.error("Ошибка при получении детальной информации о кампаниях: {}", e.getMessage(), e);
            throw new RestClientException("Ошибка при получении детальной информации о кампаниях: " + e.getMessage(), e);
        }
    }

    /**
     * Получение статистики по кампаниям.
     * GET /adv/v3/fullstats - параметры передаются через query string.
     *
     * @param apiKey API ключ продавца
     * @param request запрос статистики
     * @return статистика по кампаниям
     */
    public PromotionFullStatsResponse getPromotionFullStats(String apiKey, PromotionFullStatsRequest request) {
        HttpHeaders headers = createAuthHeaders(apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // Формируем URL с query параметрами через UriComponentsBuilder
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(promotionBaseUrl + PROMOTION_FULLSTATS_ENDPOINT);
        
        addQueryParameters(uriBuilder, request);
        
        String url = uriBuilder.toUriString();

        log.info("Запрос статистики кампаний: {} кампаний за период {} - {}",
                request.getAdvertId() != null ? request.getAdvertId().size() : 0,
                request.getDateFrom(), request.getDateTo());
        
        log.info("URL запроса статистики: {}", url);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            validateResponse(response);

            // API возвращает массив статистики по кампаниям напрямую
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

        } catch (Exception e) {
            log.error("Ошибка при получении статистики кампаний: {}", e.getMessage(), e);
            throw new RestClientException("Ошибка при получении статистики кампаний: " + e.getMessage(), e);
        }
    }

    /**
     * Получение детальной информации об аукционных кампаниях (тип 9).
     * Использует GET запрос с query параметром ids (максимум 50 ID за запрос).
     *
     * @param apiKey API ключ продавца
     * @param campaignIds список ID кампаний (максимум 50)
     * @return детальная информация о кампаниях в формате AuctionAdvertsResponse
     */
    public AuctionAdvertsResponse getAuctionAdverts(String apiKey, List<Long> campaignIds) {
        HttpHeaders headers = createAuthHeaders(apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        // Формируем URL с query параметром ids
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(promotionBaseUrl + AUCTION_ADVERTS_ENDPOINT);
        if (campaignIds != null && !campaignIds.isEmpty()) {
            // Максимум 50 ID за запрос
            List<Long> idsToRequest = campaignIds.size() > 50 
                    ? campaignIds.subList(0, 50) 
                    : campaignIds;
            String idsParam = idsToRequest.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            uriBuilder.queryParam("ids", idsParam);
        }
        String url = uriBuilder.toUriString();

        log.info("Запрос детальной информации об аукционных кампаниях: {}", url);
        log.info("Количество кампаний в запросе: {}", campaignIds != null ? campaignIds.size() : 0);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            validateResponse(response);

            // API возвращает объект с полем adverts, содержащим массив кампаний
            AuctionAdvertsResponse advertsResponse = objectMapper.readValue(
                    response.getBody(),
                    AuctionAdvertsResponse.class
            );

            log.info("Получено {} аукционных кампаний", 
                    advertsResponse.getAdverts() != null ? advertsResponse.getAdverts().size() : 0);

            return advertsResponse;

        } catch (Exception e) {
            log.error("Ошибка при получении детальной информации об аукционных кампаниях: {}", e.getMessage(), e);
            throw new RestClientException("Ошибка при получении детальной информации об аукционных кампаниях: " + e.getMessage(), e);
        }
    }
    
    /**
     * Конвертирует аукционную кампанию в формат PromotionAdvertsResponse.Campaign.
     *
     * @param auctionCampaign аукционная кампания
     * @return кампания в формате PromotionAdvertsResponse.Campaign
     */
    public PromotionAdvertsResponse.Campaign convertAuctionToPromotionCampaign(AuctionAdvertsResponse.AuctionCampaign auctionCampaign) {
        if (auctionCampaign == null) {
            return null;
        }
        
        // Извлекаем nmIds из nm_settings
        List<Long> nmIds = null;
        if (auctionCampaign.getNmSettings() != null) {
            nmIds = auctionCampaign.getNmSettings().stream()
                    .map(AuctionAdvertsResponse.NmSetting::getNmId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
        }
        
        // Определяем bidType (unified -> 2, manual -> 1)
        Integer bidType = null;
        if (auctionCampaign.getBidType() != null) {
            bidType = "unified".equals(auctionCampaign.getBidType()) ? 2 : 1;
        }
        
        return PromotionAdvertsResponse.Campaign.builder()
                .advertId(auctionCampaign.getId())
                .name(auctionCampaign.getSettings() != null ? auctionCampaign.getSettings().getName() : null)
                .type(9) // Тип 9 для аукционных кампаний
                .status(auctionCampaign.getStatus()) // Статус из корня объекта кампании
                .bidType(bidType)
                .startTime(auctionCampaign.getTimestamps() != null ? auctionCampaign.getTimestamps().getStarted() : null)
                .endTime(null) // В аукционных кампаниях нет endTime
                .createTime(auctionCampaign.getTimestamps() != null ? auctionCampaign.getTimestamps().getCreated() : null)
                .changeTime(auctionCampaign.getTimestamps() != null ? auctionCampaign.getTimestamps().getUpdated() : null)
                .nmIds(nmIds)
                .build();
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

