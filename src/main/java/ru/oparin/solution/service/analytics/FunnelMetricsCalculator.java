package ru.oparin.solution.service.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.oparin.solution.dto.analytics.AggregatedMetricsDto;
import ru.oparin.solution.dto.analytics.PeriodDto;
import ru.oparin.solution.model.ProductCard;
import ru.oparin.solution.model.ProductCardAnalytics;
import ru.oparin.solution.repository.ProductCardAnalyticsRepository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Калькулятор метрик воронки продаж.
 */
@Component
@RequiredArgsConstructor
public class FunnelMetricsCalculator {

    private final ProductCardAnalyticsRepository analyticsRepository;

    /**
     * Рассчитывает метрики воронки для периода.
     */
    public void calculateFunnelMetrics(
            AggregatedMetricsDto metrics,
            List<ProductCard> cards,
            PeriodDto period
    ) {
        FunnelTotals totals = aggregateFunnelData(cards, period);
        setBasicMetrics(metrics, totals);
        calculateConversions(metrics, totals);
    }

    private FunnelTotals aggregateFunnelData(List<ProductCard> cards, PeriodDto period) {
        int transitions = 0;
        int cart = 0;
        int orders = 0;
        BigDecimal ordersAmount = BigDecimal.ZERO;

        for (ProductCard card : cards) {
            List<ProductCardAnalytics> analytics = getCardAnalytics(card.getNmId(), period);
            FunnelTotals cardTotals = aggregateCardAnalytics(analytics);
            
            transitions += cardTotals.transitions();
            cart += cardTotals.cart();
            orders += cardTotals.orders();
            ordersAmount = ordersAmount.add(cardTotals.ordersAmount());
        }

        return new FunnelTotals(transitions, cart, orders, ordersAmount);
    }

    private List<ProductCardAnalytics> getCardAnalytics(Long nmId, PeriodDto period) {
        return analyticsRepository.findByProductCardNmIdAndDateBetween(
                nmId,
                period.getDateFrom(),
                period.getDateTo()
        );
    }

    private FunnelTotals aggregateCardAnalytics(List<ProductCardAnalytics> analytics) {
        int transitions = 0;
        int cart = 0;
        int orders = 0;
        BigDecimal ordersAmount = BigDecimal.ZERO;

        for (ProductCardAnalytics analyticsItem : analytics) {
            transitions += MathUtils.getValueOrZero(analyticsItem.getOpenCard());
            cart += MathUtils.getValueOrZero(analyticsItem.getAddToCart());
            orders += MathUtils.getValueOrZero(analyticsItem.getOrders());
            ordersAmount = ordersAmount.add(MathUtils.getValueOrZero(analyticsItem.getOrdersSum()));
        }

        return new FunnelTotals(transitions, cart, orders, ordersAmount);
    }

    private void setBasicMetrics(AggregatedMetricsDto metrics, FunnelTotals totals) {
        metrics.setTransitions(totals.transitions());
        metrics.setCart(totals.cart());
        metrics.setOrders(totals.orders());
        metrics.setOrdersAmount(totals.ordersAmount());
    }

    private void calculateConversions(AggregatedMetricsDto metrics, FunnelTotals totals) {
        if (totals.transitions() > 0) {
            BigDecimal cartConversion = MathUtils.calculatePercentage(
                    totals.cart(),
                    totals.transitions()
            );
            metrics.setCartConversion(cartConversion);
        }

        if (totals.cart() > 0) {
            BigDecimal orderConversion = MathUtils.calculatePercentage(
                    totals.orders(),
                    totals.cart()
            );
            metrics.setOrderConversion(orderConversion);
        }
    }

    private record FunnelTotals(
            int transitions,
            int cart,
            int orders,
            BigDecimal ordersAmount
    ) {
    }
}
