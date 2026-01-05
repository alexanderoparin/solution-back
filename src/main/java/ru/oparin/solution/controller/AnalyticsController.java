package ru.oparin.solution.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.oparin.solution.dto.analytics.ArticleResponseDto;
import ru.oparin.solution.dto.analytics.MetricGroupResponseDto;
import ru.oparin.solution.dto.analytics.StockSizeDto;
import ru.oparin.solution.dto.analytics.SummaryRequestDto;
import ru.oparin.solution.dto.analytics.SummaryResponseDto;
import ru.oparin.solution.service.AnalyticsService;
import ru.oparin.solution.service.SellerContextService;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Контроллер для работы с аналитикой.
 */
@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final SellerContextService sellerContextService;

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
                request.getSellerId()
        );
        
        SummaryResponseDto response = analyticsService.getSummary(
                context.user(), 
                request.getPeriods(), 
                request.getExcludedNmIds()
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
                request.getSellerId()
        );
        
        // Декодируем название метрики из URL
        String decodedMetricName = URLDecoder.decode(metricName, StandardCharsets.UTF_8);
        
        MetricGroupResponseDto response = analyticsService.getMetricGroup(
                context.user(), 
                decodedMetricName, 
                request.getPeriods(),
                request.getExcludedNmIds()
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
                request.getSellerId()
        );
        
        ArticleResponseDto response = analyticsService.getArticle(
                context.user(), 
                nmId, 
                request.getPeriods()
        );
        
        return ResponseEntity.ok(response);
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
    public ResponseEntity<java.util.List<StockSizeDto>> getStockSizes(
            @PathVariable Long nmId,
            @PathVariable String warehouseName,
            @RequestParam(required = false) Long sellerId,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = sellerContextService.createContext(
                authentication,
                sellerId
        );
        
        // Проверяем, что артикул принадлежит продавцу
        analyticsService.findCardBySeller(nmId, context.user().getId());
        
        java.util.List<StockSizeDto> response = analyticsService.getStockSizes(nmId, warehouseName);
        
        return ResponseEntity.ok(response);
    }

}

