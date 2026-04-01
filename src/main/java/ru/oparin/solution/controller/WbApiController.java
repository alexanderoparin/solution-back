package ru.oparin.solution.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.oparin.solution.dto.wb.*;
import ru.oparin.solution.service.CardsListRequestBuilder;
import ru.oparin.solution.service.ProductCardService;
import ru.oparin.solution.service.ProductStocksService;
import ru.oparin.solution.service.SellerContextService;
import ru.oparin.solution.service.events.WbApiEventService;
import ru.oparin.solution.service.wb.WbCommonApiClient;
import ru.oparin.solution.service.wb.WbContentApiClient;
import ru.oparin.solution.validation.AnalyticsPeriodValidator;

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
    private final WbApiEventService wbApiEventService;
    private final ProductStocksService stocksService;

    @PostMapping("/cards/list")
    public ResponseEntity<CardsListResponse> getCardsList(
            @Valid @RequestBody(required = false) CardsListRequest request,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = sellerContextService.createContext(authentication, null, cabinetId);
        CardsListRequest requestWithDefaults = CardsListRequestBuilder.withDefaults(request);
        CardsListResponse response = contentApiClient.getCardsList(context.apiKey(), requestWithDefaults);
        
        productCardService.saveOrUpdateCards(response, context.user());
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/cards/search")
    public ResponseEntity<CardsListResponse> searchCardsByVendorCode(
            @RequestParam String vendorCode,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = sellerContextService.createContext(authentication, null, cabinetId);
        CardsListRequest searchRequest = CardsListRequestBuilder.createSearchRequest(vendorCode);
        CardsListResponse response = contentApiClient.getCardsList(context.apiKey(), searchRequest);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ping")
    public ResponseEntity<PingResponse> ping(
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = sellerContextService.createContext(authentication, null, cabinetId);
        PingResponse response = contentApiClient.ping(context.apiKey());
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/cards/trash")
    public ResponseEntity<CardsListResponse> getCardsTrash(
            @Valid @RequestBody(required = false) CardsListRequest request,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = sellerContextService.createContext(authentication, null, cabinetId);
        CardsListRequest requestWithDefaults = CardsListRequestBuilder.withDefaults(request);
        CardsListResponse response = contentApiClient.getCardsTrash(context.apiKey(), requestWithDefaults);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/seller-info")
    public ResponseEntity<SellerInfoResponse> getSellerInfo(
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = sellerContextService.createContext(authentication, null, cabinetId);
        SellerInfoResponse response = commonApiClient.getSellerInfo(context.apiKey());
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/cards/update-analytics")
    public ResponseEntity<Map<String, String>> updateCardsAndLoadAnalytics(
            @Valid @RequestBody AnalyticsRequest request,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = sellerContextService.createContext(authentication, null, cabinetId);
        
        AnalyticsPeriodValidator.validate(request.getDateFrom(), request.getDateTo());

        wbApiEventService.enqueueInitialContentEvent(
                context.cabinet().getId(),
                request.getDateFrom(),
                request.getDateTo(),
                false,
                "SELLER_WB_API"
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("message", "Обновление поставлено в очередь событий WB API"));
    }

    @GetMapping("/warehouses/update")
    public ResponseEntity<Map<String, String>> updateWbWarehouses(
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = sellerContextService.createContext(authentication, null, cabinetId);

        wbApiEventService.enqueueWarehousesSyncCabinetEvent(context.cabinet().getId(), "SELLER_WB_API");

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("message", "Обновление складов WB поставлено в очередь событий"));
    }

    @PostMapping("/stocks/{nmId}")
    public ResponseEntity<WbStocksSizesResponse> getStocksBySizes(
            @PathVariable Long nmId,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = sellerContextService.createContext(authentication, null, cabinetId);
        
        WbStocksSizesResponse response = stocksService.getWbStocksBySizes(
                context.apiKey(),
                nmId
        );
        
        return ResponseEntity.ok(response);
    }
}
