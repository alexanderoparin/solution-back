package ru.oparin.solution.service.analytics;

import ru.oparin.solution.dto.analytics.CampaignDto;
import ru.oparin.solution.dto.analytics.CampaignPageResponse;

import java.math.BigDecimal;
import java.text.Collator;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Фильтрация, сортировка и нарезка списка РК для постраничной выдачи.
 */
public final class CampaignListPaging {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> KNOWN_STATUSES = Set.of("active", "paused", "finished");

    private CampaignListPaging() {
    }

    /**
     * Применяет поиск, статусы, тип, сортировку и пагинацию к полному списку РК.
     *
     * @param all       все кампании кабинета за период
     * @param search    подстрока названия или ID
     * @param statuses  active / paused / finished; {@code null} — без фильтра по статусу
     * @param type      отображаемый тип или {@code null}
     * @param sortBy    поле сортировки
     * @param sortDir   asc или desc
     * @param page      номер страницы с нуля
     * @param size      размер страницы
     * @return страница и справочник типов кабинета
     */
    public static CampaignPageResponse toPage(
            List<CampaignDto> all,
            String search,
            List<String> statuses,
            String type,
            String sortBy,
            String sortDir,
            int page,
            int size
    ) {
        return toPage(all, search, statuses, type, sortBy, sortDir, page, size, false, null);
    }

    /**
     * Постраничная выдача с опциями управления РК: без завершённых и фильтр по статусу биддера.
     *
     * @param excludeFinished  исключить РК со статусом WB 7 (завершена)
     * @param bidderStatus     all / running / waiting / off; {@code null} или all — без фильтра
     */
    public static CampaignPageResponse toPage(
            List<CampaignDto> all,
            String search,
            List<String> statuses,
            String type,
            String sortBy,
            String sortDir,
            int page,
            int size,
            boolean excludeFinished,
            String bidderStatus
    ) {
        List<CampaignDto> source = all == null ? List.of() : all;
        if (excludeFinished) {
            List<CampaignDto> withoutFinished = new ArrayList<>();
            for (CampaignDto campaign : source) {
                if (!isFinished(campaign)) {
                    withoutFinished.add(campaign);
                }
            }
            source = withoutFinished;
        }
        Collator ruCollator = Collator.getInstance(new Locale("ru"));
        List<String> types = source.stream()
                .map(CampaignDto::getType)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted(ruCollator)
                .collect(Collectors.toList());

        List<String> statusFilter = normalizeStatuses(statuses);
        List<CampaignDto> filtered = new ArrayList<>();
        for (CampaignDto campaign : source) {
            if (!matchesStatuses(campaign, statusFilter)) {
                continue;
            }
            if (!matchesBidderStatus(campaign, bidderStatus)) {
                continue;
            }
            if (type != null && !type.isBlank() && !type.equals(campaign.getType())) {
                continue;
            }
            if (!matchesSearch(campaign, search)) {
                continue;
            }
            filtered.add(campaign);
        }

        boolean ascending = sortDir != null && "asc".equalsIgnoreCase(sortDir.trim());
        filtered.sort(comparatorFor(sortBy, ascending, ruCollator));

        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        int total = filtered.size();
        int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) pageSize);
        int pageNumber = Math.max(page, 0);
        if (totalPages > 0 && pageNumber >= totalPages) {
            pageNumber = totalPages - 1;
        }
        int from = pageNumber * pageSize;
        List<CampaignDto> content = from >= total
                ? List.of()
                : filtered.subList(from, Math.min(from + pageSize, total));

        return CampaignPageResponse.builder()
                .content(List.copyOf(content))
                .totalElements(total)
                .totalPages(totalPages)
                .size(pageSize)
                .number(pageNumber)
                .types(types)
                .build();
    }

    /**
     * Пустой список статусов после нормализации означает «ничего не выбрано».
     * {@code null} — фильтр по статусу не применяем.
     */
    private static List<String> normalizeStatuses(List<String> statuses) {
        if (statuses == null) {
            return null;
        }
        List<String> normalized = new ArrayList<>();
        for (String raw : statuses) {
            if (raw == null) {
                continue;
            }
            String key = raw.trim().toLowerCase(Locale.ROOT);
            if (KNOWN_STATUSES.contains(key)) {
                normalized.add(key);
            }
        }
        return normalized;
    }

    private static boolean matchesStatuses(CampaignDto campaign, List<String> statuses) {
        if (statuses == null) {
            return true;
        }
        if (statuses.isEmpty()) {
            return false;
        }
        for (String key : statuses) {
            if (matchesStatus(campaign, key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesStatus(CampaignDto campaign, String key) {
        Integer status = campaign.getStatus();
        String statusName = campaign.getStatusName() == null ? "" : campaign.getStatusName().toLowerCase(Locale.ROOT);
        if ("active".equals(key)) {
            return Integer.valueOf(9).equals(status) || statusName.contains("актив");
        }
        if ("finished".equals(key)) {
            return Integer.valueOf(7).equals(status) || statusName.contains("завершен");
        }
        if (Integer.valueOf(9).equals(status) || Integer.valueOf(7).equals(status)) {
            return false;
        }
        return !statusName.contains("актив") && !statusName.contains("завершен");
    }

    /**
     * Завершённая РК в WB (как на «Управление РК»: {@code status != 7}).
     */
    private static boolean isFinished(CampaignDto campaign) {
        return Integer.valueOf(7).equals(campaign.getStatus());
    }

    private static boolean matchesBidderStatus(CampaignDto campaign, String bidderStatus) {
        if (bidderStatus == null || bidderStatus.isBlank()) {
            return true;
        }
        String key = bidderStatus.trim().toLowerCase(Locale.ROOT);
        if ("all".equals(key)) {
            return true;
        }
        String value = campaign.getBidderStatus() == null ? "" : campaign.getBidderStatus();
        if ("running".equals(key)) {
            return "RUNNING".equals(value);
        }
        if ("waiting".equals(key)) {
            return "WAITING".equals(value) || "SLOT_LIMIT".equals(value) || "NO_BUDGET".equals(value);
        }
        if ("off".equals(key)) {
            return "OFF".equals(value);
        }
        return true;
    }

    private static boolean matchesSearch(CampaignDto campaign, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String needle = search.trim().toLowerCase(Locale.ROOT);
        String name = campaign.getName() == null ? "" : campaign.getName().toLowerCase(Locale.ROOT);
        String id = campaign.getId() == null ? "" : String.valueOf(campaign.getId());
        return name.contains(needle) || id.contains(search.trim());
    }

    private static Comparator<CampaignDto> comparatorFor(String sortBy, boolean ascending, Collator ruCollator) {
        String field = sortBy == null ? "createdAt" : sortBy.trim();
        Comparator<CampaignDto> comparator = switch (field) {
            case "updatedAt" -> Comparator.comparing(CampaignListPaging::updatedAtMillis);
            case "name" -> Comparator.comparing(CampaignListPaging::nameOrEmpty, ruCollator);
            case "id" -> Comparator.comparing(campaign -> campaign.getId() == null ? 0L : campaign.getId());
            case "type" -> Comparator.comparing(CampaignListPaging::typeOrEmpty, ruCollator);
            case "articlesCount" -> Comparator.comparingInt(campaign -> nullToZero(campaign.getArticlesCount()));
            case "status" -> Comparator.comparingInt(campaign -> campaign.getStatus() == null ? -1 : campaign.getStatus());
            case "bidderStatus" -> Comparator.comparing(CampaignListPaging::bidderStatusOrEmpty, ruCollator);
            case "views" -> Comparator.comparingInt(campaign -> nullToZero(campaign.getViews()));
            case "clicks" -> Comparator.comparingInt(campaign -> nullToZero(campaign.getClicks()));
            case "ctr" -> Comparator.comparing(CampaignListPaging::ctrOrZero);
            case "cpc" -> Comparator.comparing(CampaignListPaging::cpcOrZero);
            case "costs" -> Comparator.comparing(CampaignListPaging::costsOrZero);
            case "cart" -> Comparator.comparingInt(campaign -> nullToZero(campaign.getCart()));
            case "orders" -> Comparator.comparingInt(campaign -> nullToZero(campaign.getOrders()));
            default -> Comparator.comparing(CampaignListPaging::createdAtMillis);
        };
        return ascending ? comparator : comparator.reversed();
    }

    private static long createdAtMillis(CampaignDto campaign) {
        return toMillis(campaign.getCreatedAt());
    }

    private static long updatedAtMillis(CampaignDto campaign) {
        return toMillis(campaign.getUpdatedAt());
    }

    private static long toMillis(LocalDateTime value) {
        if (value == null) {
            return 0L;
        }
        return value.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static String nameOrEmpty(CampaignDto campaign) {
        return campaign.getName() == null ? "" : campaign.getName();
    }

    private static String typeOrEmpty(CampaignDto campaign) {
        return campaign.getType() == null ? "" : campaign.getType();
    }

    private static String bidderStatusOrEmpty(CampaignDto campaign) {
        return campaign.getBidderStatus() == null ? "" : campaign.getBidderStatus();
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static BigDecimal ctrOrZero(CampaignDto campaign) {
        return campaign.getCtr() == null ? BigDecimal.ZERO : campaign.getCtr();
    }

    private static BigDecimal cpcOrZero(CampaignDto campaign) {
        return campaign.getCpc() == null ? BigDecimal.ZERO : campaign.getCpc();
    }

    private static BigDecimal costsOrZero(CampaignDto campaign) {
        return campaign.getCosts() == null ? BigDecimal.ZERO : campaign.getCosts();
    }
}
