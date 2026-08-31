package ru.oparin.solution.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.oparin.solution.dto.PageResponse;
import ru.oparin.solution.dto.analytics.CampaignControlEnqueueResponse;
import ru.oparin.solution.dto.analytics.manage.*;
import ru.oparin.solution.model.MarketplaceType;
import ru.oparin.solution.model.User;
import ru.oparin.solution.service.SellerContextService;
import ru.oparin.solution.service.UserService;
import ru.oparin.solution.service.WbPromotionCampaignControlService;
import ru.oparin.solution.service.WbPromotionCampaignControlWriteService;
import ru.oparin.solution.service.campaign.WbCampaignGoalService;
import ru.oparin.solution.service.campaign.WbCampaignManageAccessService;
import ru.oparin.solution.service.campaign.WbCampaignManageService;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Supplier;

/**
 * API управления рекламной кампанией (автопополнение, расписание, журнал).
 */
@RestController
@RequestMapping("/advertising/campaigns/{advertId}/manage")
@RequiredArgsConstructor
public class WbCampaignManageController {

    private final SellerContextService sellerContextService;
    private final WbCampaignManageService manageService;
    private final WbCampaignGoalService campaignGoalService;
    private final WbCampaignManageAccessService campaignManageAccessService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<CampaignManageResponseDto> getManage(
            @PathVariable Long advertId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication
    ) {
        SellerContextService.SellerContext ctx = sellerContextService.createContext(authentication, sellerId, cabinetId, ru.oparin.solution.model.CabinetAccessSection.CAMPAIGN_MANAGE);
        if (isOzonCabinet(ctx)) {
            return ResponseEntity.notFound().build();
        }
        Long resolvedCabinetId = ctx.cabinet() != null ? ctx.cabinet().getId() : null;
        CampaignManageResponseDto dto = manageService.getManage(advertId, resolvedCabinetId, ctx.user());
        if (dto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/balance-sources")
    public ResponseEntity<BalanceSourcesResponseDto> balanceSources(
            @PathVariable Long advertId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication
    ) {
        SellerContextService.SellerContext ctx = sellerContextService.createContext(authentication, sellerId, cabinetId, ru.oparin.solution.model.CabinetAccessSection.CAMPAIGN_MANAGE);
        if (ctx.cabinet() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(manageService.balanceSources(ctx.cabinet().getId()));
    }

    @PostMapping("/balance-sources/refresh")
    public ResponseEntity<?> refreshBalanceSources(
            @PathVariable Long advertId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication
    ) {
        SellerContextService.SellerContext ctx = sellerContextService.createContext(authentication, sellerId, cabinetId, ru.oparin.solution.model.CabinetAccessSection.CAMPAIGN_MANAGE);
        if (ctx.cabinet() == null) {
            return ResponseEntity.badRequest().build();
        }
        requireCampaignManageWrite(ctx, authentication);
        BalanceRefreshResponseDto result = manageService.refreshBalanceSources(ctx.cabinet().getId());
        if (result.getNextAvailableInSeconds() != null && !result.isRefreshed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(result);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/budget-chart")
    public ResponseEntity<?> budgetChart(
            @PathVariable Long advertId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            @RequestParam(required = false) Integer hours,
            @RequestParam(required = false) Integer stepHours,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            Authentication authentication
    ) {
        SellerContextService.SellerContext ctx = sellerContextService.createContext(authentication, sellerId, cabinetId, ru.oparin.solution.model.CabinetAccessSection.CAMPAIGN_MANAGE);
        Long cabId = ctx.cabinet() != null ? ctx.cabinet().getId() : null;
        if (cabId == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            return ResponseEntity.ok(manageService.budgetChart(advertId, cabId, hours, stepHours, from, to));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/auto-budget")
    public ResponseEntity<?> saveAutoBudget(
            @PathVariable Long advertId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            @RequestBody CampaignAutoBudgetRequestDto request,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = ctx(sellerId, cabinetId, authentication);
        User actor = currentUser(authentication);
        return write(context, authentication, () -> manageService.saveAutoBudget(
                advertId, requireCabinet(context), actor, request));
    }

    @PutMapping("/auto-budget/enabled")
    public ResponseEntity<?> setAutoBudgetEnabled(
            @PathVariable Long advertId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            @RequestBody CampaignAutoBudgetEnabledRequestDto request,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = ctx(sellerId, cabinetId, authentication);
        User actor = currentUser(authentication);
        return write(context, authentication, () -> manageService.setAutoBudgetEnabled(
                advertId, requireCabinet(context), actor, request.isEnabled()));
    }

    @PostMapping("/budget/top-up")
    public ResponseEntity<?> manualTopUp(
            @PathVariable Long advertId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            @Valid @RequestBody CampaignManualTopUpRequestDto request,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = ctx(sellerId, cabinetId, authentication);
        User actor = currentUser(authentication);
        return write(context, authentication, () -> manageService.manualTopUp(
                advertId, requireCabinet(context), actor, request));
    }

    @PostMapping("/slots")
    public ResponseEntity<?> createSlots(
            @PathVariable Long advertId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            @RequestBody WbCampaignScheduleSlotRequestDto request,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = ctx(sellerId, cabinetId, authentication);
        User actor = currentUser(authentication);
        return write(context, authentication, () -> manageService.createSlots(
                advertId, requireCabinet(context), actor, request));
    }

    @PutMapping("/slots/{slotId}")
    public ResponseEntity<?> updateSlot(
            @PathVariable Long advertId,
            @PathVariable Long slotId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            @RequestBody WbCampaignScheduleSlotUpdateDto request,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = ctx(sellerId, cabinetId, authentication);
        User actor = currentUser(authentication);
        return write(context, authentication, () -> manageService.updateSlot(
                advertId, requireCabinet(context), slotId, actor, request));
    }

    @DeleteMapping("/slots/{slotId}")
    public ResponseEntity<Void> deleteSlot(
            @PathVariable Long advertId,
            @PathVariable Long slotId,
            @RequestParam(required = false, defaultValue = "false") boolean deleteAll,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = ctx(sellerId, cabinetId, authentication);
        Long cabId = context.cabinet() != null ? context.cabinet().getId() : null;
        if (cabId == null) {
            return ResponseEntity.badRequest().build();
        }
        requireCampaignManageWrite(context, authentication);
        try {
            manageService.deleteSlot(advertId, cabId, slotId, currentUser(authentication), deleteAll);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/start")
    public ResponseEntity<?> manualStart(
            @PathVariable Long advertId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication
    ) {
        return control(advertId, sellerId, cabinetId, authentication, true);
    }

    @PostMapping("/pause")
    public ResponseEntity<?> manualPause(
            @PathVariable Long advertId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication
    ) {
        return control(advertId, sellerId, cabinetId, authentication, false);
    }

    /**
     * Сохраняет текст «Цель на рекламную кампанию».
     */
    @PutMapping("/campaign-goal")
    public ResponseEntity<Void> updateWbCampaignGoal(
            @PathVariable Long advertId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            @Valid @RequestBody UpdateWbCampaignGoalRequest request,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = ctx(sellerId, cabinetId, authentication);
        Long cabId = context.cabinet() != null ? context.cabinet().getId() : null;
        if (cabId == null) {
            return ResponseEntity.badRequest().build();
        }
        requireCampaignManageWrite(context, authentication);
        try {
            campaignGoalService.upsertGoal(cabId, advertId, request.getGoal());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/change-log")
    public ResponseEntity<PageResponse<WbCampaignChangeLogEntryDto>> changeLog(
            @PathVariable Long advertId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        SellerContextService.SellerContext context = ctx(sellerId, cabinetId, authentication);
        Long cabId = context.cabinet() != null ? context.cabinet().getId() : null;
        if (cabId == null) {
            return ResponseEntity.badRequest().build();
        }
        var logPage = manageService.changeLogPage(advertId, cabId, page, size);
        PageResponse<WbCampaignChangeLogEntryDto> response = PageResponse.<WbCampaignChangeLogEntryDto>builder()
                .content(logPage.getContent())
                .totalElements(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .size(logPage.getSize())
                .number(logPage.getNumber())
                .build();
        return ResponseEntity.ok(response);
    }

    private SellerContextService.SellerContext ctx(Long sellerId, Long cabinetId, Authentication authentication) {
        return sellerContextService.createContext(authentication, sellerId, cabinetId, ru.oparin.solution.model.CabinetAccessSection.CAMPAIGN_MANAGE);
    }

    private User currentUser(Authentication authentication) {
        return userService.findByEmail(authentication.getName());
    }

    private static Long requireCabinet(SellerContextService.SellerContext context) {
        if (context.cabinet() == null) {
            throw new IllegalArgumentException("Кабинет не выбран");
        }
        return context.cabinet().getId();
    }

    private <T> ResponseEntity<?> write(
            SellerContextService.SellerContext context,
            Authentication authentication,
            Supplier<T> action
    ) {
        try {
            requireCabinet(context);
            requireCampaignManageWrite(context, authentication);
            return ResponseEntity.ok(action.get());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (WbPromotionCampaignControlWriteService.CampaignControlWriteBlockedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (ru.oparin.solution.exception.UserException e) {
            if (e.getHttpStatus() == HttpStatus.FORBIDDEN) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "error", WbCampaignManageAccessService.SUBSCRIPTION_REQUIRED_CODE,
                        "message", e.getMessage()
                ));
            }
            throw e;
        }
    }

    private void requireCampaignManageWrite(
            SellerContextService.SellerContext context,
            Authentication authentication
    ) {
        campaignManageAccessService.requireAccess(currentUser(authentication), context.cabinet());
    }

    private ResponseEntity<?> control(
            Long advertId, Long sellerId, Long cabinetId, Authentication authentication, boolean start
    ) {
        SellerContextService.SellerContext context = ctx(sellerId, cabinetId, authentication);
        User actor = currentUser(authentication);
        try {
            requireCampaignManageWrite(context, authentication);
            Long cabId = requireCabinet(context);
            CampaignControlEnqueueResponse response = start
                    ? manageService.manualStart(advertId, cabId, actor)
                    : manageService.manualPause(advertId, cabId, actor);
            return ResponseEntity.status(response.enqueued() ? HttpStatus.ACCEPTED : HttpStatus.OK).body(response);
        } catch (WbPromotionCampaignControlService.CampaignControlRateLimitException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Превышен лимит WB API", "nextAvailableInSeconds", e.getNextAvailableInSeconds()));
        } catch (WbPromotionCampaignControlWriteService.CampaignControlWriteBlockedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (ru.oparin.solution.exception.UserException e) {
            if (e.getHttpStatus() == HttpStatus.FORBIDDEN) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "error", WbCampaignManageAccessService.SUBSCRIPTION_REQUIRED_CODE,
                        "message", e.getMessage()
                ));
            }
            throw e;
        }
    }

    private static boolean isOzonCabinet(SellerContextService.SellerContext ctx) {
        return ctx.cabinet() != null && ctx.cabinet().getMarketplaceType() == MarketplaceType.OZON;
    }
}
