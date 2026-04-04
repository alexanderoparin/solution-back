package ru.oparin.solution.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.oparin.solution.dto.analytics.CampaignDetailDto;
import ru.oparin.solution.dto.analytics.CampaignDto;
import ru.oparin.solution.dto.analytics.PromotionSyncEnqueueResponse;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.service.AnalyticsService;
import ru.oparin.solution.service.SellerContextService;
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
}
