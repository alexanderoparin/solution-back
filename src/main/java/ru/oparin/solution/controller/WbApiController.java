package ru.oparin.solution.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.oparin.solution.dto.wb.CardsListRequest;
import ru.oparin.solution.dto.wb.CardsListResponse;
import ru.oparin.solution.dto.wb.PingResponse;
import ru.oparin.solution.dto.wb.SellerInfoResponse;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.User;
import ru.oparin.solution.model.WbApiKey;
import ru.oparin.solution.service.UserService;
import ru.oparin.solution.service.WbApiClient;
import ru.oparin.solution.service.WbApiKeyService;

/**
 * Контроллер для работы с WB API.
 */
@RestController
@RequestMapping("/wb-api")
@RequiredArgsConstructor
public class WbApiController {

    private static final int DEFAULT_LIMIT = 100;

    private final WbApiClient wbApiClient;
    private final WbApiKeyService wbApiKeyService;
    private final UserService userService;

    /**
     * Получение списка карточек товаров селлера.
     * 
     * @param request параметры запроса
     * @param authentication данные аутентификации
     * @return список карточек товаров
     */
    @PostMapping("/cards/list")
    public ResponseEntity<CardsListResponse> getCardsList(
            @Valid @RequestBody(required = false) CardsListRequest request,
            Authentication authentication
    ) {
        SellerContext context = createSellerContext(authentication);
        CardsListRequest requestWithDefaults = buildRequestWithDefaults(request);
        CardsListResponse response = wbApiClient.getCardsList(context.getApiKey(), requestWithDefaults);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Поиск карточек товаров по артикулу (vendorCode).
     * 
     * @param vendorCode артикул продавца для поиска
     * @param authentication данные аутентификации
     * @return список найденных карточек товаров
     */
    @GetMapping("/cards/search")
    public ResponseEntity<CardsListResponse> searchCardsByVendorCode(
            @RequestParam String vendorCode,
            Authentication authentication
    ) {
        SellerContext context = createSellerContext(authentication);
        CardsListRequest searchRequest = buildSearchRequest(vendorCode);
        CardsListResponse response = wbApiClient.getCardsList(context.getApiKey(), searchRequest);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Проверка подключения к WB API.
     * 
     * @param authentication данные аутентификации
     * @return ответ с временной меткой и статусом подключения
     */
    @GetMapping("/ping")
    public ResponseEntity<PingResponse> ping(Authentication authentication) {
        SellerContext context = createSellerContext(authentication);
        PingResponse response = wbApiClient.ping(context.getApiKey());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Получение списка карточек товаров из корзины (trash).
     * 
     * @param request параметры запроса
     * @param authentication данные аутентификации
     * @return список карточек товаров из корзины
     */
    @PostMapping("/cards/trash")
    public ResponseEntity<CardsListResponse> getCardsTrash(
            @Valid @RequestBody(required = false) CardsListRequest request,
            Authentication authentication
    ) {
        SellerContext context = createSellerContext(authentication);
        CardsListRequest requestWithDefaults = buildRequestWithDefaults(request);
        CardsListResponse response = wbApiClient.getCardsTrash(context.getApiKey(), requestWithDefaults);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Получение информации о продавце.
     * 
     * @param authentication данные аутентификации
     * @return информация о продавце
     */
    @GetMapping("/seller-info")
    public ResponseEntity<SellerInfoResponse> getSellerInfo(Authentication authentication) {
        SellerContext context = createSellerContext(authentication);
        SellerInfoResponse response = wbApiClient.getSellerInfo(context.getApiKey());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Создает контекст продавца (пользователь + API ключ) с проверкой роли.
     */
    private SellerContext createSellerContext(Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        validateSellerRole(user);
        
        WbApiKey apiKey = wbApiKeyService.findByUserId(user.getId());
        return new SellerContext(user, apiKey.getApiKey());
    }

    /**
     * Проверяет, что пользователь имеет роль SELLER.
     */
    private void validateSellerRole(User user) {
        if (user.getRole() != Role.SELLER) {
            throw new UserException("Только SELLER может выполнять эту операцию");
        }
    }

    /**
     * Строит запрос с значениями по умолчанию, если они не указаны.
     */
    private CardsListRequest buildRequestWithDefaults(CardsListRequest request) {
        if (request != null && request.getSettings() != null) {
            return request;
        }
        return createDefaultRequest();
    }

    /**
     * Создает запрос с значениями по умолчанию.
     */
    private CardsListRequest createDefaultRequest() {
        CardsListRequest.Cursor cursor = CardsListRequest.Cursor.builder()
                .limit(DEFAULT_LIMIT)
                .build();
        CardsListRequest.Settings settings = CardsListRequest.Settings.builder()
                .cursor(cursor)
                .build();
        return CardsListRequest.builder()
                .settings(settings)
                .build();
    }

    /**
     * Строит запрос для поиска по артикулу.
     */
    private CardsListRequest buildSearchRequest(String vendorCode) {
        CardsListRequest.Cursor cursor = CardsListRequest.Cursor.builder()
                .limit(DEFAULT_LIMIT)
                .build();
        CardsListRequest.Filter filter = CardsListRequest.Filter.builder()
                .textSearch(vendorCode)
                .build();
        CardsListRequest.Settings settings = CardsListRequest.Settings.builder()
                .cursor(cursor)
                .filter(filter)
                .build();
        return CardsListRequest.builder()
                .settings(settings)
                .build();
    }

    /**
     * Внутренний класс для хранения контекста продавца.
     */
    private static class SellerContext {
        private final String apiKey;

        public SellerContext(User user, String apiKey) {
            this.apiKey = apiKey;
        }

        public String getApiKey() {
            return apiKey;
        }
    }
}
