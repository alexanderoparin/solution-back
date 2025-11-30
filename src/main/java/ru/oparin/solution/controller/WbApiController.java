package ru.oparin.solution.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.oparin.solution.dto.wb.AnalyticsRequest;
import ru.oparin.solution.dto.wb.CardsListRequest;
import ru.oparin.solution.dto.wb.CardsListResponse;
import ru.oparin.solution.dto.wb.PingResponse;
import ru.oparin.solution.dto.wb.SellerInfoResponse;
import ru.oparin.solution.dto.wb.WbWarehouseResponse;
import ru.oparin.solution.service.CardsListRequestBuilder;
import ru.oparin.solution.service.ProductCardAnalyticsService;
import ru.oparin.solution.service.ProductCardService;
import ru.oparin.solution.service.SellerContextService;
import ru.oparin.solution.service.WbWarehouseService;
import ru.oparin.solution.service.wb.WbCommonApiClient;
import ru.oparin.solution.service.wb.WbContentApiClient;
import ru.oparin.solution.service.wb.WbWarehousesApiClient;
import ru.oparin.solution.validation.AnalyticsPeriodValidator;

import java.util.List;
import java.util.Map;

/**
 * Контроллер для работы с WB API.
 */
@RestController
@RequestMapping("/wb-api")
@RequiredArgsConstructor
public class WbApiController {

    private final WbContentApiClient contentApiClient;
    private final WbCommonApiClient commonApiClient;
    private final SellerContextService sellerContextService;
    private final ProductCardService productCardService;
    private final ProductCardAnalyticsService analyticsService;
    private final WbWarehousesApiClient warehousesApiClient;
    private final WbWarehouseService warehouseService;

    @PostMapping("/cards/list")
    public ResponseEntity<CardsListResponse> getCardsList(
            @Valid @RequestBody(required = false) CardsListRequest request,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = sellerContextService.createContext(authentication);
        CardsListRequest requestWithDefaults = CardsListRequestBuilder.withDefaults(request);
        CardsListResponse response = contentApiClient.getCardsList(context.apiKey(), requestWithDefaults);
        
        productCardService.saveOrUpdateCards(response, context.user());
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/cards/search")
    public ResponseEntity<CardsListResponse> searchCardsByVendorCode(
            @RequestParam String vendorCode,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = sellerContextService.createContext(authentication);
        CardsListRequest searchRequest = CardsListRequestBuilder.createSearchRequest(vendorCode);
        CardsListResponse response = contentApiClient.getCardsList(context.apiKey(), searchRequest);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ping")
    public ResponseEntity<PingResponse> ping(Authentication authentication) {
        SellerContextService.SellerContext context = sellerContextService.createContext(authentication);
        PingResponse response = contentApiClient.ping(context.apiKey());
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/cards/trash")
    public ResponseEntity<CardsListResponse> getCardsTrash(
            @Valid @RequestBody(required = false) CardsListRequest request,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = sellerContextService.createContext(authentication);
        CardsListRequest requestWithDefaults = CardsListRequestBuilder.withDefaults(request);
        CardsListResponse response = contentApiClient.getCardsTrash(context.apiKey(), requestWithDefaults);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/seller-info")
    public ResponseEntity<SellerInfoResponse> getSellerInfo(Authentication authentication) {
        SellerContextService.SellerContext context = sellerContextService.createContext(authentication);
        SellerInfoResponse response = commonApiClient.getSellerInfo(context.apiKey());
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/cards/update-analytics")
    public ResponseEntity<Map<String, String>> updateCardsAndLoadAnalytics(
            @Valid @RequestBody AnalyticsRequest request,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = sellerContextService.createContext(authentication);
        
        AnalyticsPeriodValidator.validate(request.getDateFrom(), request.getDateTo());
        
        analyticsService.updateCardsAndLoadAnalytics(
                context.user(),
                context.apiKey(),
                request.getDateFrom(),
                request.getDateTo()
        );
        
        return ResponseEntity.ok(Map.of("message", "Обновление карточек и загрузка аналитики запущено"));
    }

    @GetMapping("/warehouses/update")
    public ResponseEntity<Map<String, String>> updateWbWarehouses(Authentication authentication) {
        SellerContextService.SellerContext context = sellerContextService.createContext(authentication);
        
        List<WbWarehouseResponse> warehouses = warehousesApiClient.getWbOffices(context.apiKey());
        warehouseService.saveOrUpdateWarehouses(warehouses);
        
        return ResponseEntity.ok(Map.of("message", "Обновление складов WB завершено"));
    }
}
