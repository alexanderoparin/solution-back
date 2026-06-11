package ru.oparin.solution.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.oparin.solution.dto.analytics.*;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.User;
import ru.oparin.solution.service.*;
import ru.oparin.solution.service.campaign.CampaignManageAccessService;
import ru.oparin.solution.service.events.WbApiEventService;
import ru.oparin.solution.service.events.payload.MainStepPayload;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * API для раздела «Реклама»: список рекламных кампаний кабинета.
 */
@RestController
@RequestMapping("/advertising")
@RequiredArgsConstructor
public class AdvertisingController {

    private static final int SYNC_PROMOTION_DEFAULT_PERIOD_DAYS = 14;

    private final SellerContextService sellerContextService;
    private final AnalyticsService analyticsService;
    private final WbApiEventService wbApiEventService;
    private final PromotionCampaignControlService promotionCampaignControlService;
    private final PromotionCampaignControlWriteService promotionCampaignControlWriteService;
    private final CampaignManageAccessService campaignManageAccessService;
    private final UserService userService;

    /**
     * Список рекламных кампаний текущего кабинета (с агрегацией статистики за период).
     * Период задаётся dateFrom и dateTo (ISO yyyy-MM-dd). По умолчанию — последние 14 дней.
     *
     * @param sellerId ID селлера (опционально, для ADMIN/MANAGER)
     * @param cabinetId ID кабинета (опционально)
     * @param dateFrom начало периода (опционально)
     * @param dateTo конец периода (опционально)
     * @param authentication данные аутентификации
     * @return список кампаний
     */
    @GetMapping("/campaigns")
    public ResponseEntity<List<CampaignDto>> listCampaigns(
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = sellerContextService.createContext(
                authentication,
                sellerId,
                cabinetId
        );
        Long resolvedCabinetId = context.cabinet() != null ? context.cabinet().getId() : null;
        List<CampaignDto> campaigns = analyticsService.listCampaignsByCabinet(resolvedCabinetId, dateFrom, dateTo);
        return ResponseEntity.ok(campaigns);
    }

    /**
     * Поставить в очередь обновление списка РК и статистики за период (цепочка PROMOTION_COUNT → … → fullstats).
     * Период как у списка кампаний: по умолчанию последние 14 дней до сегодня.
     */
    @PostMapping("/campaigns/promotion-sync")
    public ResponseEntity<?> enqueuePromotionSync(
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = sellerContextService.createContext(
                authentication,
                sellerId,
                cabinetId
        );
        Cabinet cabinet = context.cabinet();
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "У кабинета нет API-ключа"));
        }
        LocalDate to = dateTo != null ? dateTo : LocalDate.now();
        LocalDate from = dateFrom != null ? dateFrom : to.minusDays(SYNC_PROMOTION_DEFAULT_PERIOD_DAYS - 1);
        if (from.isAfter(to)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }
        MainStepPayload payload = MainStepPayload.builder()
                .dateFrom(from)
                .dateTo(to)
                .includeStocks(false)
                .build();
        boolean enqueued = wbApiEventService.enqueuePromotionRequestLevelEvents(
                cabinet.getId(),
                payload,
                "ADVERTISING_UI"
        );
        return ResponseEntity.ok(new PromotionSyncEnqueueResponse(enqueued));
    }

    /**
     * Доступность запуска/паузы РК для кабинета (временная блокировка при read-only токене).
     */
    @GetMapping("/campaigns/control-capabilities")
    public ResponseEntity<PromotionControlCapabilitiesDto> getControlCapabilities(
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = sellerContextService.createContext(
                authentication,
                sellerId,
                cabinetId
        );
        Cabinet cabinet = context.cabinet();
        if (cabinet == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(promotionCampaignControlWriteService.getCapabilities(cabinet));
    }

    /**
     * Поставить в очередь запуск рекламной кампании (WB GET /adv/v0/start).
     */
    @PostMapping("/campaigns/{advertId}/start")
    public ResponseEntity<?> startCampaign(
            @PathVariable Long advertId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication
    ) {
        return enqueueCampaignControl(advertId, sellerId, cabinetId, authentication, true);
    }

    /**
     * Поставить в очередь паузу рекламной кампании (WB GET /adv/v0/pause).
     */
    @PostMapping("/campaigns/{advertId}/pause")
    public ResponseEntity<?> pauseCampaign(
            @PathVariable Long advertId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication
    ) {
        return enqueueCampaignControl(advertId, sellerId, cabinetId, authentication, false);
    }

    private ResponseEntity<?> enqueueCampaignControl(
            Long advertId,
            Long sellerId,
            Long cabinetId,
            Authentication authentication,
            boolean start
    ) {
        SellerContextService.SellerContext context = sellerContextService.createContext(
                authentication,
                sellerId,
                cabinetId
        );
        Cabinet cabinet = context.cabinet();
        if (cabinet == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Кабинет не выбран"));
        }
        try {
            User actor = userService.findByEmail(authentication.getName());
            campaignManageAccessService.requireAccess(actor, context.user());
            CampaignControlEnqueueResponse response = start
                    ? promotionCampaignControlService.enqueueStart(cabinet, advertId)
                    : promotionCampaignControlService.enqueuePause(cabinet, advertId);
            return ResponseEntity.status(response.enqueued() ? HttpStatus.ACCEPTED : HttpStatus.OK)
                    .body(response);
        } catch (PromotionCampaignControlService.CampaignControlRateLimitException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(
                            "message",
                            formatRateLimitMessage(e.getNextAvailableInSeconds()),
                            "nextAvailableInSeconds",
                            e.getNextAvailableInSeconds()
                    ));
        } catch (PromotionCampaignControlWriteService.CampaignControlWriteBlockedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "message", e.getMessage(),
                            "nextAvailableInSeconds", e.getNextAvailableInSeconds(),
                            "canControl", false
                    ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (UserException e) {
            if (e.getHttpStatus() == HttpStatus.FORBIDDEN) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "error", CampaignManageAccessService.SUBSCRIPTION_REQUIRED_CODE,
                        "message", e.getMessage()
                ));
            }
            throw e;
        }
    }

    private static String formatRateLimitMessage(long seconds) {
        if (seconds >= 60) {
            long minutes = (seconds + 59) / 60;
            return "Превышен лимит запросов к WB API. Повторите примерно через "
                    + minutes + " мин.";
        }
        return "Превышен лимит запросов к WB API. Повторите через " + seconds + " сек.";
    }

    /**
     * Детали рекламной кампании (комбо): название, статус, список артикулов.
     */
    @GetMapping("/campaigns/{id}")
    public ResponseEntity<CampaignDetailDto> getCampaignDetail(
            @PathVariable Long id,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = sellerContextService.createContext(
                authentication,
                sellerId,
                cabinetId
        );
        Long resolvedCabinetId = context.cabinet() != null ? context.cabinet().getId() : null;
        Long resolvedSellerId = context.user() != null ? context.user().getId() : null;
        CampaignDetailDto detail = analyticsService.getCampaignDetail(id, resolvedCabinetId, resolvedSellerId);
        if (detail == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(detail);
    }

    /**
     * Статистика по поисковым кластерам кампании за период (из БД после синхронизации normquery).
     */
    @GetMapping("/campaigns/{id}/normquery-clusters")
    public ResponseEntity<NormQueryClustersResponseDto> getCampaignNormQueryClusters(
            @PathVariable Long id,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Long nmId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "clicks") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = sellerContextService.createContext(
                authentication,
                sellerId,
                cabinetId
        );
        Long resolvedCabinetId = context.cabinet() != null ? context.cabinet().getId() : null;
        Long resolvedSellerId = context.user() != null ? context.user().getId() : null;
        NormQueryClustersResponseDto response = analyticsService.getCampaignNormQueryClusters(
                id, resolvedCabinetId, resolvedSellerId, from, to, nmId, search, sortBy, sortDir, page, size);
        if (response == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(response);
    }
}
