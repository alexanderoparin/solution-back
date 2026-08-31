package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.*;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.model.WbApiEventStatus;
import ru.oparin.solution.model.WbApiEventType;
import ru.oparin.solution.repository.WbApiEventRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Админские выборки очереди WB API событий.
 */
@Service
@RequiredArgsConstructor
public class WbApiEventAdminQuery {

    private final WbApiEventRepository eventRepository;

    /**
     * Счётчики по статусам.
     */
    @Transactional(readOnly = true)
    public WbApiEventStatsDto getStats() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (WbApiEventStatus status : WbApiEventStatus.values()) {
            byStatus.put(status.name(), eventRepository.countByStatus(status));
        }
        return WbApiEventStatsDto.builder()
                .total(eventRepository.count())
                .byStatus(byStatus)
                .build();
    }

    /**
     * Счётчики по типам событий (опционально в рамках статуса).
     */
    @Transactional(readOnly = true)
    public WbApiEventTypeStatsDto getStatsByType(WbApiEventStatus status) {
        Map<String, Long> byType = new LinkedHashMap<>();
        for (WbApiEventType type : WbApiEventType.values()) {
            byType.put(type.name(), 0L);
        }
        List<Object[]> rows = eventRepository.countGroupedByEventType(status);
        for (Object[] row : rows) {
            WbApiEventType eventType = (WbApiEventType) row[0];
            Long count = (Long) row[1];
            byType.put(eventType.name(), count);
        }
        long total = byType.values().stream().mapToLong(Long::longValue).sum();
        return WbApiEventTypeStatsDto.builder()
                .baseStatus(status != null ? status.name() : null)
                .total(total)
                .byType(byType)
                .build();
    }

    /**
     * Счётчики по кабинетам.
     */
    @Transactional(readOnly = true)
    public WbApiEventCabinetStatsDto getStatsByCabinet(WbApiEventStatus status, WbApiEventType eventType) {
        List<WbApiEventCabinetStatsItemDto> byCabinet = new ArrayList<>();
        List<Object[]> rows = eventRepository.countGroupedByCabinetId(status, eventType);
        for (Object[] row : rows) {
            Long cabinetId = (Long) row[0];
            String cabinetName = (String) row[1];
            Long count = (Long) row[2];
            byCabinet.add(WbApiEventCabinetStatsItemDto.builder()
                    .cabinetId(cabinetId)
                    .cabinetName(cabinetName)
                    .count(count != null ? count : 0L)
                    .build());
        }
        long total = byCabinet.stream().mapToLong(WbApiEventCabinetStatsItemDto::count).sum();
        return WbApiEventCabinetStatsDto.builder()
                .baseStatus(status != null ? status.name() : null)
                .baseEventType(eventType != null ? eventType.name() : null)
                .total(total)
                .byCabinet(byCabinet)
                .build();
    }

    /**
     * Страница событий для админки.
     */
    @Transactional(readOnly = true)
    public PageResponse<WbApiEventDto> getEventsPage(
            int page,
            int size,
            WbApiEventStatus status,
            WbApiEventType eventType,
            Long cabinetId,
            WbApiEventSortField sortBy,
            Sort.Direction sortDir
    ) {
        Sort sort = sortForAdminEvents(sortBy, sortDir);
        Pageable pageable = PageRequest.of(page, Math.clamp(size, 1, 100), sort);
        Page<WbApiEvent> eventsPage = eventRepository.findAdminEvents(status, eventType, cabinetId, pageable);
        List<WbApiEventDto> content = eventsPage.getContent().stream().map(this::toDto).toList();
        return PageResponse.<WbApiEventDto>builder()
                .content(content)
                .totalElements(eventsPage.getTotalElements())
                .totalPages(eventsPage.getTotalPages())
                .size(eventsPage.getSize())
                .number(eventsPage.getNumber())
                .build();
    }

    /**
     * Одно событие по id.
     */
    @Transactional(readOnly = true)
    public WbApiEventDto getEventById(Long eventId) {
        WbApiEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Событие не найдено: " + eventId));
        return toDto(event);
    }

    private static Sort sortForAdminEvents(WbApiEventSortField sortBy, Sort.Direction sortDir) {
        WbApiEventSortField effectiveSortBy = sortBy != null ? sortBy : WbApiEventSortField.ID;
        Sort.Direction effectiveDir = sortDir != null ? sortDir : Sort.Direction.DESC;
        Order order = new Order(effectiveDir, effectiveSortBy.getFieldPath());
        if (effectiveSortBy == WbApiEventSortField.STARTED_AT
                || effectiveSortBy == WbApiEventSortField.FINISHED_AT
                || effectiveSortBy == WbApiEventSortField.NEXT_ATTEMPT_AT) {
            order = order.nullsLast();
        }
        return Sort.by(order);
    }

    private WbApiEventDto toDto(WbApiEvent event) {
        return WbApiEventDto.builder()
                .id(event.getId())
                .eventType(event.getEventType().name())
                .status(event.getStatus().name())
                .executorBeanName(event.getExecutorBeanName())
                .cabinetId(event.getCabinet() != null ? event.getCabinet().getId() : null)
                .cabinetName(event.getCabinet() != null ? event.getCabinet().getName() : null)
                .dedupKey(event.getDedupKey())
                .attemptCount(event.getAttemptCount())
                .maxAttempts(event.getMaxAttempts())
                .nextAttemptAt(event.getNextAttemptAt())
                .lastError(event.getLastError())
                .priority(event.getPriority())
                .triggerSource(event.getTriggerSource())
                .createdAt(event.getCreatedAt())
                .startedAt(event.getStartedAt())
                .finishedAt(event.getFinishedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }
}
