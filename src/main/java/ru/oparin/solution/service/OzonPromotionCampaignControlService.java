package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import ru.oparin.solution.dto.analytics.CampaignControlEnqueueResponse;
import ru.oparin.solution.dto.analytics.PromotionControlCapabilitiesDto;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.MarketplaceType;
import ru.oparin.solution.model.OzonPromotionCampaign;
import ru.oparin.solution.repository.OzonPromotionCampaignRepository;
import ru.oparin.solution.service.ozon.OzonPerformanceApiClient;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Запуск и пауза рекламных кампаний Ozon Performance API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OzonPromotionCampaignControlService {

    private static final Set<String> START_ALLOWED = Set.of(
            "CAMPAIGN_STATE_INACTIVE",
            "CAMPAIGN_STATE_STOPPED",
            "CAMPAIGN_STATE_PLANNED"
    );
    private static final Set<String> PAUSE_ALLOWED = Set.of("CAMPAIGN_STATE_RUNNING");

    private final OzonPromotionCampaignRepository campaignRepository;
    private final OzonPerformanceApiClient performanceApiClient;
    private final OzonPerformanceCredentialsService credentialsService;

    /**
     * Возможность управлять РК: нужны валидные Performance credentials.
     */
    public PromotionControlCapabilitiesDto getCapabilities(Cabinet cabinet) {
        if (cabinet == null || cabinet.getMarketplaceType() != MarketplaceType.OZON) {
            return PromotionControlCapabilitiesDto.allowed();
        }
        if (!credentialsService.hasUsableCredentials(cabinet)) {
            return new PromotionControlCapabilitiesDto(
                    false,
                    "Задайте и проверьте Performance credentials Ozon в настройках кабинета",
                    0L,
                    null
            );
        }
        return PromotionControlCapabilitiesDto.allowed();
    }

    /**
     * Активирует кампанию в Ozon и обновляет статус в БД.
     */
    @Transactional
    public CampaignControlEnqueueResponse activate(Cabinet cabinet, Long campaignId) {
        OzonPromotionCampaign campaign = requireCampaign(cabinet, campaignId);
        requireCredentials(cabinet);
        if (!START_ALLOWED.contains(normalizeState(campaign.getState()))) {
            throw new IllegalArgumentException(
                    "Кампанию нельзя запустить из статуса «" + formatState(campaign.getState()) + "»");
        }
        try {
            performanceApiClient.activateCampaign(
                    cabinet.getId(),
                    cabinet.getOzonPerformanceClientId().trim(),
                    cabinet.getOzonPerformanceClientSecret().trim(),
                    campaignId
            );
            campaign.setState("CAMPAIGN_STATE_RUNNING");
            campaign.setOzonUpdatedAt(LocalDateTime.now());
            campaign.setSyncedAt(LocalDateTime.now());
            campaignRepository.save(campaign);
            log.info("Ozon campaign activate: cabinetId={}, campaignId={}", cabinet.getId(), campaignId);
            return new CampaignControlEnqueueResponse(true, null, "Кампания запущена");
        } catch (HttpClientErrorException e) {
            throw toUserException(e, "запуске");
        } catch (RestClientException e) {
            throw new UserException("Ошибка связи с Ozon Performance API: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    /**
     * Останавливает кампанию в Ozon и обновляет статус в БД.
     */
    @Transactional
    public CampaignControlEnqueueResponse deactivate(Cabinet cabinet, Long campaignId) {
        OzonPromotionCampaign campaign = requireCampaign(cabinet, campaignId);
        requireCredentials(cabinet);
        if (!PAUSE_ALLOWED.contains(normalizeState(campaign.getState()))) {
            throw new IllegalArgumentException(
                    "Кампанию нельзя остановить из статуса «" + formatState(campaign.getState()) + "»");
        }
        try {
            performanceApiClient.deactivateCampaign(
                    cabinet.getId(),
                    cabinet.getOzonPerformanceClientId().trim(),
                    cabinet.getOzonPerformanceClientSecret().trim(),
                    campaignId
            );
            campaign.setState("CAMPAIGN_STATE_STOPPED");
            campaign.setOzonUpdatedAt(LocalDateTime.now());
            campaign.setSyncedAt(LocalDateTime.now());
            campaignRepository.save(campaign);
            log.info("Ozon campaign deactivate: cabinetId={}, campaignId={}", cabinet.getId(), campaignId);
            return new CampaignControlEnqueueResponse(true, null, "Кампания остановлена");
        } catch (HttpClientErrorException e) {
            throw toUserException(e, "остановке");
        } catch (RestClientException e) {
            throw new UserException("Ошибка связи с Ozon Performance API: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    private OzonPromotionCampaign requireCampaign(Cabinet cabinet, Long campaignId) {
        return campaignRepository.findByCampaignIdAndCabinet_Id(campaignId, cabinet.getId())
                .orElseThrow(() -> new IllegalArgumentException("Кампания Ozon не найдена в этом кабинете"));
    }

    private void requireCredentials(Cabinet cabinet) {
        if (!credentialsService.hasUsableCredentials(cabinet)) {
            throw new UserException(
                    "Сначала задайте и проверьте Performance credentials Ozon",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private static String normalizeState(String state) {
        return state != null ? state.trim() : "";
    }

    private static String formatState(String state) {
        if (state == null || state.isBlank()) {
            return "неизвестно";
        }
        return state.replace("CAMPAIGN_STATE_", "").toLowerCase();
    }

    private static UserException toUserException(HttpClientErrorException e, String actionRu) {
        int status = e.getStatusCode().value();
        if (status == 401 || status == 403) {
            return new UserException(
                    "Ozon Performance API отклонил " + actionRu + " кампании (нет доступа). Проверьте credentials.",
                    HttpStatus.FORBIDDEN
            );
        }
        if (status == 429) {
            return new UserException(
                    "Превышен лимит запросов к Ozon Performance API. Повторите позже.",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }
        String body = e.getResponseBodyAsString();
        String detail = (body != null && !body.isBlank() && body.length() < 300) ? body : ("HTTP " + status);
        return new UserException("Ошибка при " + actionRu + " кампании Ozon: " + detail, HttpStatus.BAD_REQUEST);
    }
}
