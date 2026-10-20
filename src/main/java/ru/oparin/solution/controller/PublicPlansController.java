package ru.oparin.solution.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.oparin.solution.config.SubscriptionProperties;
import ru.oparin.solution.dto.PlanDto;
import ru.oparin.solution.model.Plan;
import ru.oparin.solution.model.PlanKind;
import ru.oparin.solution.repository.PlanRepository;
import ru.oparin.solution.service.PlanMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Публичный каталог тарифов (без авторизации) — для лендинга.
 */
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicPlansController {

    private final PlanRepository planRepository;
    private final SubscriptionProperties subscriptionProperties;

    /**
     * Каталог продаваемых планов. Опционально {@code kind}: MAIN | CAMPAIGN | AB_PACK.
     */
    @GetMapping("/plans")
    public ResponseEntity<List<PlanDto>> getPlans(
            @RequestParam(required = false) String kind
    ) {
        if (kind != null && !kind.isBlank()) {
            PlanKind planKind = PlanKind.valueOf(kind.trim().toUpperCase());
            if (planKind == PlanKind.CAMPAIGN && !subscriptionProperties.isCampaignManagementEnabled()) {
                return ResponseEntity.ok(List.of());
            }
            return ResponseEntity.ok(
                    planRepository.findByIsActiveTrueAndKindOrderBySortOrderAsc(planKind).stream()
                            .filter(this::isPurchasable)
                            .map(PlanMapper::toDto)
                            .toList()
            );
        }

        List<PlanDto> list = new ArrayList<>();
        list.addAll(planRepository.findByIsActiveTrueAndKindOrderBySortOrderAsc(PlanKind.MAIN).stream()
                .filter(this::isPurchasable)
                .map(PlanMapper::toDto)
                .toList());
        if (subscriptionProperties.isCampaignManagementEnabled()) {
            list.addAll(planRepository.findByIsActiveTrueAndKindOrderBySortOrderAsc(PlanKind.CAMPAIGN).stream()
                    .filter(this::isPurchasable)
                    .map(PlanMapper::toDto)
                    .toList());
        }
        list.addAll(planRepository.findByIsActiveTrueAndKindOrderBySortOrderAsc(PlanKind.AB_PACK).stream()
                .filter(this::isPurchasable)
                .map(PlanMapper::toDto)
                .toList());
        return ResponseEntity.ok(list);
    }

    private boolean isPurchasable(Plan plan) {
        return plan.getPriceRub() != null && plan.getPriceRub().compareTo(BigDecimal.ZERO) > 0;
    }
}
