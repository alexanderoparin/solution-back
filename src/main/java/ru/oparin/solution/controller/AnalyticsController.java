package ru.oparin.solution.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.oparin.solution.dto.analytics.*;
import ru.oparin.solution.service.AnalyticsService;
import ru.oparin.solution.service.ArticleAdCampaignGoalService;
import ru.oparin.solution.service.SellerContextService;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Контроллер для работы с аналитикой.
 */
@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final SellerContextService sellerContextService;
    private final ArticleAdCampaignGoalService articleAdCampaignGoalService;

    /**
     * Получает список артикулов кабинета/продавца (только справочная информация для фильтра).
     *
     * @param sellerId ID продавца (опционально, для ADMIN/MANAGER)
     * @param cabinetId ID кабинета (опционально)
     * @param authentication данные аутентификации
     * @return список артикулов с полями nmId, title, brand, subjectName, photoTm и т.д.
     */
    @GetMapping("/articles")
    public ResponseEntity<List<ArticleSummaryDto>> getArticles(
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            @RequestParam(required = false) Boolean onlyWithPhoto,
            @RequestParam(required = false) Boolean onlyPriority,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = sellerContextService.createContext(
                authentication,
                sellerId,
                cabinetId
        );

        List<ArticleSummaryDto> response = analyticsService.getArticleList(
                context.user(),
                context.cabinetId(),
                onlyWithPhoto,
                onlyPriority
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Получает сводную аналитику для продавца.
     *
     * @param request запрос с периодами и исключенными артикулами
     * @param authentication данные аутентификации
     * @return сводная аналитика
     */
    @PostMapping("/summary")
    public ResponseEntity<SummaryResponseDto> getSummary(
            @RequestBody SummaryRequestDto request,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = sellerContextService.createContext(
                authentication,
                request.getSellerId(),
                request.getCabinetId()
        );

        SummaryResponseDto response = analyticsService.getSummary(
                context.user(),
                context.cabinetId(),
                request.getPeriods(),
                request.getExcludedNmIds(),
                request.getPage(),
                request.getSize(),
                request.getSearch(),
                request.getIncludedNmIds(),
                request.getFilterToNone(),
                request.getOnlyWithPhoto(),
                request.getOnlyPriority()
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Получает детальные метрики по группе для всех артикулов.
     *
     * @param metricName название метрики
     * @param request запрос с периодами и исключенными артикулами
     * @param authentication данные аутентификации
     * @return детальные метрики по группе
     */
    @PostMapping("/summary/metrics/{metricName}")
    public ResponseEntity<MetricGroupResponseDto> getMetricGroup(
            @PathVariable String metricName,
            @RequestBody SummaryRequestDto request,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = sellerContextService.createContext(
                authentication,
                request.getSellerId(),
                request.getCabinetId()
        );

        String decodedMetricName = URLDecoder.decode(metricName, StandardCharsets.UTF_8);

        MetricGroupResponseDto response = analyticsService.getMetricGroup(
                context.user(),
                context.cabinetId(),
                decodedMetricName,
                request.getPeriods(),
                request.getExcludedNmIds(),
                request.getOnlyWithPhoto(),
                request.getOnlyPriority()
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Получает детальную информацию по артикулу.
     *
     * @param nmId артикул товара
     * @param request запрос с периодами
     * @param authentication данные аутентификации
     * @return детальная информация по артикулу
     */
    @PostMapping("/article/{nmId}")
    public ResponseEntity<ArticleResponseDto> getArticle(
            @PathVariable Long nmId,
            @RequestBody SummaryRequestDto request,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = sellerContextService.createContext(
                authentication,
                request.getSellerId(),
                request.getCabinetId()
        );

        ArticleResponseDto response = analyticsService.getArticle(
                context.user(),
                context.cabinetId(),
                nmId,
                request.getPeriods(),
                request.getCampaignDateFrom(),
                request.getCampaignDateTo()
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Сохраняет текст «Цель рекламной кампании» для артикула в выбранном кабинете.
     */
    @PutMapping("/article/{nmId}/ad-campaign-goal")
    public ResponseEntity<Void> updateAdCampaignGoal(
            @PathVariable Long nmId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            @Valid @RequestBody UpdateAdCampaignGoalRequest request,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = sellerContextService.createContext(
                authentication,
                sellerId,
                cabinetId
        );
        articleAdCampaignGoalService.upsertGoal(context.user(), context.cabinetId(), nmId, request.getGoal());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PutMapping("/article/{nmId}/priority")
    public ResponseEntity<Void> updateArticlePriority(
            @PathVariable Long nmId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            @Valid @RequestBody UpdateArticlePriorityRequest request,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = sellerContextService.createContext(
                authentication,
                sellerId,
                cabinetId
        );
        analyticsService.updateArticlePriority(context.user(), context.cabinetId(), nmId, request.getPriority());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * Получает детализацию остатков по размерам для товара на конкретном складе.
     *
     * @param nmId артикул товара
     * @param warehouseName название склада
     * @param sellerId ID продавца (опционально, для ADMIN/MANAGER)
     * @param authentication данные аутентификации
     * @return список остатков по размерам
     */
    @GetMapping("/article/{nmId}/stocks/{warehouseName}/sizes")
    public ResponseEntity<List<StockSizeDto>> getStockSizes(
            @PathVariable Long nmId,
            @PathVariable String warehouseName,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = sellerContextService.createContext(
                authentication,
                sellerId,
                cabinetId
        );
        
        // Проверяем, что артикул принадлежит продавцу
        analyticsService.findCardBySeller(nmId, context.user().getId());
        
        List<StockSizeDto> response = analyticsService.getStockSizes(nmId, warehouseName);
        
        return ResponseEntity.ok(response);
    }

}

