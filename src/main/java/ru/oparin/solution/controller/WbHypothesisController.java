package ru.oparin.solution.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.oparin.solution.dto.analytics.manage.CampaignCabinetResolveDto;
import ru.oparin.solution.dto.hypothesis.WbHypothesisBulkDeleteRequest;
import ru.oparin.solution.dto.hypothesis.WbHypothesisDto;
import ru.oparin.solution.dto.hypothesis.WbHypothesisUpsertRequest;
import ru.oparin.solution.dto.hypothesis.WbHypothesisVerdictRequest;
import ru.oparin.solution.model.CabinetAccessSection;
import ru.oparin.solution.model.User;
import ru.oparin.solution.service.SellerContextService;
import ru.oparin.solution.service.UserService;
import ru.oparin.solution.service.hypothesis.WbHypothesisService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * API гипотез аналитики.
 */
@RestController
@RequestMapping("/analytics/hypotheses")
@RequiredArgsConstructor
public class WbHypothesisController {

    private final SellerContextService sellerContextService;
    private final WbHypothesisService hypothesisService;
    private final UserService userService;

    /**
     * Список гипотез кабинета.
     */
    @GetMapping
    public ResponseEntity<List<WbHypothesisDto>> list(
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long nmId,
            @RequestParam(required = false) String criterion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodTo,
            Authentication authentication
    ) {
        SellerContextService.SellerContext ctx = sellerContextService.createContext(
                authentication, sellerId, cabinetId, CabinetAccessSection.PRODUCTS);
        if (ctx.cabinet() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(hypothesisService.list(
                ctx.cabinet().getId(), search, status, nmId, criterion, periodFrom, periodTo));
    }

    /**
     * Деталка гипотезы.
     */
    @GetMapping("/{id}")
    public ResponseEntity<WbHypothesisDto> get(
            @PathVariable Long id,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication
    ) {
        SellerContextService.SellerContext ctx = sellerContextService.createContext(
                authentication, sellerId, cabinetId, CabinetAccessSection.PRODUCTS);
        if (ctx.cabinet() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(hypothesisService.get(ctx.cabinet().getId(), id));
    }

    /**
     * Кабинет гипотезы для deep-link.
     */
    @GetMapping("/{id}/cabinet")
    public ResponseEntity<CampaignCabinetResolveDto> resolveCabinet(
            @PathVariable Long id,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        return hypothesisService.resolveAccessibleCabinet(id, user)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Создание гипотезы.
     */
    @PostMapping
    public ResponseEntity<WbHypothesisDto> create(
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            @Valid @RequestBody WbHypothesisUpsertRequest request,
            Authentication authentication
    ) {
        SellerContextService.SellerContext ctx = sellerContextService.createContext(
                authentication, sellerId, cabinetId, CabinetAccessSection.PRODUCTS);
        if (ctx.cabinet() == null) {
            return ResponseEntity.badRequest().build();
        }
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(hypothesisService.create(ctx.cabinet().getId(), user.getId(), request));
    }

    /**
     * Обновление гипотезы.
     */
    @PutMapping("/{id}")
    public ResponseEntity<WbHypothesisDto> update(
            @PathVariable Long id,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            @Valid @RequestBody WbHypothesisUpsertRequest request,
            Authentication authentication
    ) {
        SellerContextService.SellerContext ctx = sellerContextService.createContext(
                authentication, sellerId, cabinetId, CabinetAccessSection.PRODUCTS);
        if (ctx.cabinet() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(hypothesisService.update(ctx.cabinet().getId(), id, request));
    }

    /**
     * Ручной итог Успешно/Неуспешно.
     */
    @PatchMapping("/{id}/verdict")
    public ResponseEntity<WbHypothesisDto> setVerdict(
            @PathVariable Long id,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            @Valid @RequestBody WbHypothesisVerdictRequest request,
            Authentication authentication
    ) {
        SellerContextService.SellerContext ctx = sellerContextService.createContext(
                authentication, sellerId, cabinetId, CabinetAccessSection.PRODUCTS);
        if (ctx.cabinet() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(hypothesisService.setVerdict(ctx.cabinet().getId(), id, request));
    }

    /**
     * Удаление гипотезы.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication
    ) {
        SellerContextService.SellerContext ctx = sellerContextService.createContext(
                authentication, sellerId, cabinetId, CabinetAccessSection.PRODUCTS);
        if (ctx.cabinet() == null) {
            return ResponseEntity.badRequest().build();
        }
        hypothesisService.delete(ctx.cabinet().getId(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Массовое удаление.
     */
    @PostMapping("/bulk-delete")
    public ResponseEntity<Map<String, Long>> bulkDelete(
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            @Valid @RequestBody WbHypothesisBulkDeleteRequest request,
            Authentication authentication
    ) {
        SellerContextService.SellerContext ctx = sellerContextService.createContext(
                authentication, sellerId, cabinetId, CabinetAccessSection.PRODUCTS);
        if (ctx.cabinet() == null) {
            return ResponseEntity.badRequest().build();
        }
        long deleted = hypothesisService.bulkDelete(ctx.cabinet().getId(), request.getIds());
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }
}
