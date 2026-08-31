package ru.oparin.solution.service.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.MarketplaceType;
import ru.oparin.solution.service.CabinetService;

/**
 * Определение маркетплейса кабинета для маршрутизации аналитики.
 */
@Component
@RequiredArgsConstructor
public class AnalyticsMarketplaceRouter {

    private final CabinetService cabinetService;

    /**
     * {@code true}, если кабинет задан и это Ozon. При {@code cabinetId == null} считается WB.
     */
    public boolean isOzon(Long cabinetId) {
        if (cabinetId == null) {
            return false;
        }
        Cabinet cabinet = cabinetService.findByIdWithUserOrThrow(cabinetId);
        return cabinet.getMarketplaceType() == MarketplaceType.OZON;
    }
}
