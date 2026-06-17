package ru.oparin.solution.service;

import ru.oparin.solution.dto.PlanDto;
import ru.oparin.solution.model.Plan;

public final class PlanMapper {

    private PlanMapper() {
    }

    public static PlanDto toDto(Plan plan) {
        if (plan == null) {
            return null;
        }
        return PlanDto.builder()
                .id(plan.getId())
                .name(plan.getName())
                .description(plan.getDescription())
                .priceRub(plan.getPriceRub())
                .periodDays(plan.getPeriodDays())
                .maxCabinets(plan.getMaxCabinets())
                .sortOrder(plan.getSortOrder())
                .isActive(plan.getIsActive())
                .code(plan.getCode())
                .periodType(plan.getPeriodType() != null ? plan.getPeriodType().name() : null)
                .build();
    }
}
