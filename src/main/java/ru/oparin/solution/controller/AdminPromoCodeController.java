package ru.oparin.solution.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import ru.oparin.solution.dto.CreatePromoCodeRequest;
import ru.oparin.solution.dto.PageResponse;
import ru.oparin.solution.dto.PromoCodeAdminDto;
import ru.oparin.solution.dto.PromoCodeRedemptionAdminDto;
import ru.oparin.solution.model.User;
import ru.oparin.solution.service.AdminPromoCodeService;
import ru.oparin.solution.service.UserService;

import java.util.List;

/**
 * Админ-эндпоинты промокодов и активаций.
 */
@RestController
@RequestMapping("/admin/promo-codes")
@RequiredArgsConstructor
public class AdminPromoCodeController {

    private final AdminPromoCodeService adminPromoCodeService;
    private final UserService userService;

    /**
     * Список промокодов.
     */
    @GetMapping
    public ResponseEntity<List<PromoCodeAdminDto>> listPromoCodes() {
        User admin = currentAdmin();
        return ResponseEntity.ok(adminPromoCodeService.listPromoCodes(admin));
    }

    /**
     * Создание промокода.
     */
    @PostMapping
    public ResponseEntity<PromoCodeAdminDto> createPromoCode(
            @Valid @RequestBody CreatePromoCodeRequest request
    ) {
        User admin = currentAdmin();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminPromoCodeService.createPromoCode(admin, request));
    }

    /**
     * Постраничный список активаций промокодов.
     */
    @GetMapping("/redemptions")
    public ResponseEntity<PageResponse<PromoCodeRedemptionAdminDto>> listRedemptions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String code
    ) {
        User admin = currentAdmin();
        return ResponseEntity.ok(adminPromoCodeService.pageRedemptions(admin, page, size, code));
    }

    private User currentAdmin() {
        return userService.findByEmail(
                SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
