package ru.oparin.solution.service.analytics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Общая пагинация и фильтр «включённые артикулы» для сводной аналитики.
 */
public final class CatalogPageHelper {

    private CatalogPageHelper() {
    }

    /**
     * Страница списка и полный размер до slice.
     */
    public record Slice<T>(List<T> items, int total) {
    }

    /**
     * {@code true}, если запрошена постраничная выдача.
     */
    public static boolean isPaginated(Integer page, Integer size) {
        return page != null && size != null && size > 0;
    }

    /**
     * filterToNone → пустой список; иначе оставляет только {@code includedIds}, если список не пуст.
     */
    public static <T> List<T> applyInclusionFilter(
            List<T> items,
            Boolean filterToNone,
            List<Long> includedIds,
            Function<T, Long> idGetter
    ) {
        if (Boolean.TRUE.equals(filterToNone)) {
            return List.of();
        }
        if (includedIds != null && !includedIds.isEmpty()) {
            Set<Long> idSet = new HashSet<>(includedIds);
            return items.stream()
                    .filter(item -> {
                        Long id = idGetter.apply(item);
                        return id != null && idSet.contains(id);
                    })
                    .collect(Collectors.toList());
        }
        return items;
    }

    /**
     * Поиск + сортировка + slice страницы. Сортировка применяется к уже отфильтрованному списку.
     */
    public static <T> Slice<T> filterSearchSortAndPaginate(
            List<T> items,
            String search,
            UnaryOperator<List<T>> searchFilter,
            UnaryOperator<List<T>> sorter,
            Integer page,
            Integer size
    ) {
        List<T> filtered = new ArrayList<>(items);
        if (search != null && !search.isBlank()) {
            filtered = new ArrayList<>(searchFilter.apply(filtered));
        }
        filtered = sorter.apply(filtered);
        return paginate(filtered, page, size);
    }

    /**
     * Срез страницы; {@code items} не должен быть unmodifiable view, если вызывающий будет его менять.
     */
    public static <T> Slice<T> paginate(List<T> items, Integer page, Integer size) {
        int total = items.size();
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);
        List<T> pageItems = from < to ? new ArrayList<>(items.subList(from, to)) : List.of();
        return new Slice<>(pageItems, total);
    }
}
