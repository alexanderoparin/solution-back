package ru.oparin.solution.service.analytics;

import ru.oparin.solution.model.ProductCard;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Фильтр артикулов по исключенным nmId.
 */
public class ProductCardFilter {

    /**
     * Фильтрует артикулы, исключая указанные nmId.
     *
     * @param allCards все артикулы
     * @param excludedNmIds список nmId для исключения (может быть null или пустым)
     * @return отфильтрованный список артикулов
     */
    public static List<ProductCard> filterVisibleCards(
            List<ProductCard> allCards,
            List<Long> excludedNmIds
    ) {
        if (allCards == null || allCards.isEmpty()) {
            return List.of();
        }

        if (excludedNmIds == null || excludedNmIds.isEmpty()) {
            return allCards;
        }

        Set<Long> excludedSet = Set.copyOf(excludedNmIds);
        return allCards.stream()
                .filter(card -> card != null && card.getNmId() != null)
                .filter(card -> !excludedSet.contains(card.getNmId()))
                .collect(Collectors.toList());
    }
}
