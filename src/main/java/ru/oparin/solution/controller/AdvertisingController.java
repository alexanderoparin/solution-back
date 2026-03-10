package ru.oparin.solution.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.oparin.solution.dto.analytics.CampaignDetailDto;
import ru.oparin.solution.dto.analytics.CampaignDto;
import ru.oparin.solution.service.AnalyticsService;
import ru.oparin.solution.service.SellerContextService;

import java.time.LocalDate;
import java.util.List;

/**
 * API для раздела «Реклама»: список рекламных кампаний кабинета.
 */
@RestController
@RequestMapping("/advertising")
@RequiredArgsConstructor
public class AdvertisingController {

    private final SellerContextService sellerContextService;
    private final AnalyticsService analyticsService;

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
