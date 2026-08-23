package ru.oparin.solution.service.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.config.OzonEventsProperties;
import ru.oparin.solution.dto.*;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.OzonApiEvent;
import ru.oparin.solution.model.OzonApiEventStatus;
import ru.oparin.solution.model.OzonApiEventType;
import ru.oparin.solution.repository.CabinetRepository;
import ru.oparin.solution.repository.OzonApiEventRepository;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.events.payload.OzonAnalyticsDataCabinetPayload;
import ru.oparin.solution.service.events.payload.OzonCampaignStatsCabinetPayload;
import ru.oparin.solution.service.events.payload.OzonPricesCabinetPayload;
import ru.oparin.solution.service.events.payload.OzonProductListPagePayload;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OzonApiEventService {

    public static final String PRODUCT_LIST_EXECUTOR_BEAN = "ozonProductListPageEventExecutor";
    public static final String PRICES_CABINET_EXECUTOR_BEAN = "ozonPricesCabinetEventExecutor";
    public static final String STOCKS_CABINET_EXECUTOR_BEAN = "ozonStocksCabinetEventExecutor";
    public static final String ANALYTICS_DATA_CABINET_EXECUTOR_BEAN = "ozonAnalyticsDataCabinetEventExecutor";
    public static final String CAMPAIGNS_CABINET_EXECUTOR_BEAN = "ozonCampaignsCabinetEventExecutor";
    public static final String CAMPAIGN_STATS_CABINET_EXECUTOR_BEAN = "ozonCampaignStatsCabinetEventExecutor";

    private static final int PRODUCT_LIST_MAX_ATTEMPTS = 3;
    private static final int PRODUCT_LIST_PRIORITY = 100;
    private static final int PRICES_MAX_ATTEMPTS = 3;
    private static final int PRICES_PRIORITY = 85;
    private static final int STOCKS_MAX_ATTEMPTS = 3;
    private static final int STOCKS_PRIORITY = 80;
    private static final int ANALYTICS_MAX_ATTEMPTS = 3;
    private static final int ANALYTICS_PRIORITY = 70;
    private static final int CAMPAIGNS_MAX_ATTEMPTS = 3;
    private static final int CAMPAIGNS_PRIORITY = 65;
    private static final int CAMPAIGN_STATS_MAX_ATTEMPTS = 15;
    private static final int CAMPAIGN_STATS_PRIORITY = 60;
    /** Суток в периоде аналитики (вчера + ещё 13 дней назад). */
    private static final int ANALYTICS_PERIOD_DAYS = 14;

    private static final Set<OzonApiEventStatus> ACTIVE_STATUSES = Set.of(
            OzonApiEventStatus.CREATED,
            OzonApiEventStatus.RUNNING,
            OzonApiEventStatus.FAILED_RETRYABLE,
            OzonApiEventStatus.DEFERRED_RATE_LIMIT
    );
    private static final List<OzonApiEventStatus> RUNNABLE_STATUSES = List.of(
            OzonApiEventStatus.CREATED,
            OzonApiEventStatus.FAILED_RETRYABLE,
            OzonApiEventStatus.DEFERRED_RATE_LIMIT
    );

    private final OzonApiEventRepository eventRepository;
    private final CabinetRepository cabinetRepository;
    private final CabinetService cabinetService;
    private final ObjectMapper objectMapper;
    private final OzonEventsProperties ozonEventsProperties;

    @Transactional
    public void enqueueInitialProductListEvent(Long cabinetId, boolean includeStocks, String triggerSource) {
        OzonProductListPagePayload payload = OzonProductListPagePayload.builder()
                .lastId("")
                .includeStocks(includeStocks)
                .build();
        enqueueProductListEvent(cabinetId, payload, buildProductListDedupKey(cabinetId, payload.lastId()), triggerSource);
    }

    @Transactional
    public void enqueueNextProductListEvent(Long cabinetId, OzonProductListPagePayload payload, String triggerSource) {
        enqueueProductListEvent(cabinetId, payload, buildProductListDedupKey(cabinetId, payload.lastId()), triggerSource);
    }

    /**
     * Загрузка цен по кабинету после завершения каталога.
     */
    @Transactional
    public void enqueuePricesCabinetEvent(Long cabinetId, boolean includeStocks, String triggerSource) {
        String dedupKey = "PRICES_CABINET:" + cabinetId;
        if (eventRepository.existsByDedupKeyAndStatusIn(dedupKey, ACTIVE_STATUSES)) {
            log.debug("Ozon PRICES event уже существует (dedupKey={}), создание пропущено", dedupKey);
            return;
        }
        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден: " + cabinetId));
        OzonPricesCabinetPayload payload = OzonPricesCabinetPayload.builder()
                .includeStocks(includeStocks)
                .build();
        OzonApiEvent event = OzonApiEvent.builder()
                .eventType(OzonApiEventType.PRICES_CABINET)
                .status(OzonApiEventStatus.CREATED)
                .executorBeanName(PRICES_CABINET_EXECUTOR_BEAN)
                .cabinet(cabinet)
                .payloadJson(writePayload(payload))
                .dedupKey(dedupKey)
                .attemptCount(0)
                .maxAttempts(PRICES_MAX_ATTEMPTS)
                .nextAttemptAt(LocalDateTime.now())
                .priority(PRICES_PRIORITY)
                .triggerSource(triggerSource)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        eventRepository.save(event);
    }

    /**
     * Загрузка остатков после цен (если includeStocks в цепочке sync).
     */
    @Transactional
    public void enqueueStocksCabinetEvent(Long cabinetId, String triggerSource) {
        String dedupKey = "STOCKS_CABINET:" + cabinetId;
        if (eventRepository.existsByDedupKeyAndStatusIn(dedupKey, ACTIVE_STATUSES)) {
            log.debug("Ozon STOCKS event уже существует (dedupKey={}), создание пропущено", dedupKey);
            return;
        }
        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден: " + cabinetId));
        OzonApiEvent event = OzonApiEvent.builder()
                .eventType(OzonApiEventType.STOCKS_CABINET)
                .status(OzonApiEventStatus.CREATED)
                .executorBeanName(STOCKS_CABINET_EXECUTOR_BEAN)
                .cabinet(cabinet)
                .payloadJson("{}")
                .dedupKey(dedupKey)
                .attemptCount(0)
                .maxAttempts(STOCKS_MAX_ATTEMPTS)
                .nextAttemptAt(LocalDateTime.now())
                .priority(STOCKS_PRIORITY)
                .triggerSource(triggerSource)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        eventRepository.save(event);
    }

    /**
     * Загрузка аналитики продаж после цен/остатков (последний шаг main-цепочки).
     */
    @Transactional
    public void enqueueAnalyticsDataCabinetEvent(Long cabinetId, String triggerSource) {
        String dedupKey = "ANALYTICS_DATA_CABINET:" + cabinetId;
        if (eventRepository.existsByDedupKeyAndStatusIn(dedupKey, ACTIVE_STATUSES)) {
            log.debug("Ozon ANALYTICS event уже существует (dedupKey={}), создание пропущено", dedupKey);
            return;
        }
        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден: " + cabinetId));
        LocalDate dateTo = LocalDate.now().minusDays(1);
        LocalDate dateFrom = dateTo.minusDays(ANALYTICS_PERIOD_DAYS - 1L);
        OzonAnalyticsDataCabinetPayload payload = OzonAnalyticsDataCabinetPayload.builder()
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .build();
        OzonApiEvent event = OzonApiEvent.builder()
                .eventType(OzonApiEventType.ANALYTICS_DATA_CABINET)
                .status(OzonApiEventStatus.CREATED)
                .executorBeanName(ANALYTICS_DATA_CABINET_EXECUTOR_BEAN)
                .cabinet(cabinet)
                .payloadJson(writePayload(payload))
                .dedupKey(dedupKey)
                .attemptCount(0)
                .maxAttempts(ANALYTICS_MAX_ATTEMPTS)
                .nextAttemptAt(LocalDateTime.now())
                .priority(ANALYTICS_PRIORITY)
                .triggerSource(triggerSource)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        eventRepository.save(event);
    }

    /**
     * Загрузка списка рекламных кампаний Ozon Performance API (отдельная цепочка от main sync).
     *
     * @return {@code true}, если событие создано; {@code false}, если уже есть активное с тем же dedupKey
     */
    @Transactional
    public boolean enqueueCampaignsCabinetEvent(Long cabinetId, String triggerSource) {
        String dedupKey = "CAMPAIGNS_CABINET:" + cabinetId;
        if (eventRepository.existsByDedupKeyAndStatusIn(dedupKey, ACTIVE_STATUSES)) {
            log.debug("Ozon CAMPAIGNS event уже существует (dedupKey={}), создание пропущено", dedupKey);
            return false;
        }
        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден: " + cabinetId));
        OzonApiEvent event = OzonApiEvent.builder()
                .eventType(OzonApiEventType.CAMPAIGNS_CABINET)
                .status(OzonApiEventStatus.CREATED)
                .executorBeanName(CAMPAIGNS_CABINET_EXECUTOR_BEAN)
                .cabinet(cabinet)
                .payloadJson("{}")
                .dedupKey(dedupKey)
                .attemptCount(0)
                .maxAttempts(CAMPAIGNS_MAX_ATTEMPTS)
                .nextAttemptAt(LocalDateTime.now())
                .priority(CAMPAIGNS_PRIORITY)
                .triggerSource(triggerSource)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        eventRepository.save(event);
        return true;
    }

    /**
     * Загрузка списка РК + дневной статистики Performance за период.
     *
     * @return {@code true}, если событие создано
     */
    @Transactional
    public boolean enqueueCampaignStatsCabinetEvent(
            Long cabinetId,
            LocalDate dateFrom,
            LocalDate dateTo,
            String triggerSource
    ) {
        LocalDate to = dateTo != null ? dateTo : LocalDate.now().minusDays(1);
        LocalDate from = dateFrom != null ? dateFrom : to.minusDays(ANALYTICS_PERIOD_DAYS - 1L);
        if (from.isAfter(to)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }
        String dedupKey = "CAMPAIGN_STATS_CABINET:" + cabinetId + ":" + from + ":" + to;
        if (eventRepository.existsByDedupKeyAndStatusIn(dedupKey, ACTIVE_STATUSES)) {
            log.debug("Ozon CAMPAIGN_STATS event уже существует (dedupKey={}), создание пропущено", dedupKey);
            return false;
        }
        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден: " + cabinetId));
        OzonCampaignStatsCabinetPayload payload = OzonCampaignStatsCabinetPayload.builder()
                .dateFrom(from)
                .dateTo(to)
                .build();
        OzonApiEvent event = OzonApiEvent.builder()
                .eventType(OzonApiEventType.CAMPAIGN_STATS_CABINET)
                .status(OzonApiEventStatus.CREATED)
                .executorBeanName(CAMPAIGN_STATS_CABINET_EXECUTOR_BEAN)
                .cabinet(cabinet)
                .payloadJson(writePayload(payload))
                .dedupKey(dedupKey)
                .attemptCount(0)
                .maxAttempts(CAMPAIGN_STATS_MAX_ATTEMPTS)
                .nextAttemptAt(LocalDateTime.now())
                .priority(CAMPAIGN_STATS_PRIORITY)
                .triggerSource(triggerSource)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        eventRepository.save(event);
        return true;
    }

    @Transactional(readOnly = true)
    public List<OzonApiEvent> findDueEvents() {
        List<String> statusNames = RUNNABLE_STATUSES.stream().map(Enum::name).toList();
        List<Long> ids = eventRepository.findReadyEventIdsOnePerCabinetAndType(statusNames, LocalDateTime.now());
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<Long, OzonApiEvent> byId = eventRepository.findAllByIdInWithCabinet(ids).stream()
                .collect(Collectors.toMap(OzonApiEvent::getId, e -> e, (a, b) -> a));
        List<OzonApiEvent> sorted = ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing(OzonApiEvent::getPriority, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(OzonApiEvent::getNextAttemptAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(OzonApiEvent::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        int batchSize = Math.max(1, ozonEventsProperties.getPollBatchSize());
        return sorted.size() <= batchSize ? sorted : sorted.subList(0, batchSize);
    }

    @Transactional
    public boolean tryMarkRunning(OzonApiEvent event) {
        return eventRepository.tryMarkRunning(
                event.getId(),
                event.getCabinet().getId(),
                event.getEventType(),
                RUNNABLE_STATUSES,
                OzonApiEventStatus.RUNNING,
                LocalDateTime.now()
        ) > 0;
    }

    @Transactional
    public void markSuccessIfRunning(Long eventId) {
        OzonApiEvent event = eventRepository.findById(eventId).orElse(null);
        if (event == null || event.getStatus() != OzonApiEventStatus.RUNNING) {
            return;
        }
        markSuccess(event);
    }

    @Transactional
    public void markFailedIfRunning(Long eventId, OzonApiEventExecutionResult result) {
        OzonApiEvent event = eventRepository.findById(eventId).orElse(null);
        if (event == null || event.getStatus() != OzonApiEventStatus.RUNNING) {
            return;
        }
        event.setStartedAt(null);
        markFailed(event, result);
    }

    @Transactional
    public void markFailed(OzonApiEvent event, OzonApiEventExecutionResult result) {
        event.setLastError(result.errorMessage());
        event.setUpdatedAt(LocalDateTime.now());

        if (result.deferUntil() != null) {
            event.setStartedAt(null);
            if (result.countsAsAttempt()) {
                int nextAttempt = event.getAttemptCount() + 1;
                event.setAttemptCount(nextAttempt);
                if (nextAttempt >= event.getMaxAttempts()) {
                    event.setStatus(OzonApiEventStatus.FAILED_FINAL);
                    event.setFinishedAt(LocalDateTime.now());
                    eventRepository.save(event);
                    return;
                }
            }
            event.setStatus(OzonApiEventStatus.DEFERRED_RATE_LIMIT);
            event.setNextAttemptAt(result.deferUntil());
            eventRepository.save(event);
            return;
        }

        int nextAttempt = event.getAttemptCount() + 1;
        event.setAttemptCount(nextAttempt);
        if (result.retryable() && nextAttempt < event.getMaxAttempts()) {
            event.setStatus(OzonApiEventStatus.FAILED_RETRYABLE);
            event.setNextAttemptAt(LocalDateTime.now().plusSeconds(30));
        } else {
            event.setStatus(OzonApiEventStatus.FAILED_FINAL);
            event.setFinishedAt(LocalDateTime.now());
        }
        eventRepository.save(event);
    }

    @Transactional
    public void markMainCompleted(Long cabinetId) {
        Cabinet cabinet = cabinetService.findByIdWithUserOrThrow(cabinetId);
        cabinet.setLastDataUpdateAt(LocalDateTime.now());
        cabinetService.save(cabinet);
    }

    /**
     * Фиксирует успешное обновление остатков Ozon (колонка «Обновление остатков» в админке).
     */
    @Transactional
    public void markStocksCompleted(Long cabinetId) {
        Cabinet cabinet = cabinetService.findByIdWithUserOrThrow(cabinetId);
        cabinet.setLastStocksUpdateAt(LocalDateTime.now());
        cabinetService.save(cabinet);
    }

    /**
     * Фиксирует успешную синхронизацию списка РК Ozon.
     */
    @Transactional
    public void markCampaignsSyncCompleted(Long cabinetId) {
        Cabinet cabinet = cabinetService.findByIdWithUserOrThrow(cabinetId);
        cabinet.setLastOzonCampaignsSyncAt(LocalDateTime.now());
        cabinetService.save(cabinet);
    }

    @Transactional
    public int recoverStuckRunningEvents(int timeoutMinutes) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<OzonApiEvent> stuck = eventRepository.findByStatusAndStartedAtBefore(OzonApiEventStatus.RUNNING, threshold);
        for (OzonApiEvent event : stuck) {
            event.setStatus(OzonApiEventStatus.FAILED_RETRYABLE);
            event.setLastError("Автовосстановление: событие было RUNNING дольше " + timeoutMinutes + " мин");
            event.setStartedAt(null);
            event.setNextAttemptAt(LocalDateTime.now().plusSeconds(10));
            event.setUpdatedAt(LocalDateTime.now());
        }
        eventRepository.saveAll(stuck);
        return stuck.size();
    }

    public <T> T readPayload(OzonApiEvent event, Class<T> payloadType) {
        try {
            return objectMapper.readValue(event.getPayloadJson(), payloadType);
        } catch (Exception e) {
            throw new IllegalArgumentException("Некорректный payload события " + event.getId() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Обновляет JSON payload события (для multi-step async, например product-stats poll).
     */
    @Transactional
    public void updateEventPayload(Long eventId, Object payload) {
        OzonApiEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Событие не найдено: " + eventId));
        event.setPayloadJson(writePayload(payload));
        event.setUpdatedAt(LocalDateTime.now());
        eventRepository.save(event);
    }

    private void enqueueProductListEvent(
            Long cabinetId,
            OzonProductListPagePayload payload,
            String dedupKey,
            String triggerSource
    ) {
        if (eventRepository.existsByDedupKeyAndStatusIn(dedupKey, ACTIVE_STATUSES)) {
            log.debug("Ozon API event уже существует (dedupKey={}), создание пропущено", dedupKey);
            return;
        }
        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден: " + cabinetId));
        OzonApiEvent event = OzonApiEvent.builder()
                .eventType(OzonApiEventType.PRODUCT_LIST_PAGE)
                .status(OzonApiEventStatus.CREATED)
                .executorBeanName(PRODUCT_LIST_EXECUTOR_BEAN)
                .cabinet(cabinet)
                .payloadJson(writePayload(payload))
                .dedupKey(dedupKey)
                .attemptCount(0)
                .maxAttempts(PRODUCT_LIST_MAX_ATTEMPTS)
                .nextAttemptAt(LocalDateTime.now())
                .priority(PRODUCT_LIST_PRIORITY)
                .triggerSource(triggerSource)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        eventRepository.save(event);
    }

    private void markSuccess(OzonApiEvent event) {
        event.setStatus(OzonApiEventStatus.SUCCESS);
        event.setFinishedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        eventRepository.save(event);
    }

    private String writePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Не удалось сериализовать payload события: " + e.getMessage(), e);
        }
    }

    private static String buildProductListDedupKey(Long cabinetId, String lastId) {
        return "PRODUCT_LIST_PAGE:" + cabinetId + ":" + (lastId == null || lastId.isBlank() ? "first" : lastId);
    }

    @Transactional
    public long deleteOldSuccessfulEvents(int hours) {
        LocalDateTime threshold = LocalDateTime.now().minusHours(hours);
        return eventRepository.deleteByStatusAndFinishedAtBefore(OzonApiEventStatus.SUCCESS, threshold);
    }

    @Transactional(readOnly = true)
    public OzonApiEventStatsDto getStats() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (OzonApiEventStatus status : OzonApiEventStatus.values()) {
            byStatus.put(status.name(), eventRepository.countByStatus(status));
        }
        return OzonApiEventStatsDto.builder()
                .total(eventRepository.count())
                .byStatus(byStatus)
                .build();
    }

    @Transactional(readOnly = true)
    public OzonApiEventTypeStatsDto getStatsByType(OzonApiEventStatus status) {
        Map<String, Long> byType = new LinkedHashMap<>();
        for (OzonApiEventType type : OzonApiEventType.values()) {
            if (!type.isQueuedEvent()) {
                continue;
            }
            byType.put(type.name(), 0L);
        }
        List<Object[]> rows = eventRepository.countGroupedByEventType(status);
        for (Object[] row : rows) {
            OzonApiEventType eventType = (OzonApiEventType) row[0];
            Long count = (Long) row[1];
            byType.put(eventType.name(), count);
        }
        long total = byType.values().stream().mapToLong(Long::longValue).sum();
        return OzonApiEventTypeStatsDto.builder()
                .baseStatus(status != null ? status.name() : null)
                .total(total)
                .byType(byType)
                .build();
    }

    @Transactional(readOnly = true)
    public OzonApiEventCabinetStatsDto getStatsByCabinet(OzonApiEventStatus status, OzonApiEventType eventType) {
        List<OzonApiEventCabinetStatsItemDto> byCabinet = new ArrayList<>();
        List<Object[]> rows = eventRepository.countGroupedByCabinetId(status, eventType);
        for (Object[] row : rows) {
            Long cabinetId = (Long) row[0];
            String cabinetName = (String) row[1];
            Long count = (Long) row[2];
            byCabinet.add(OzonApiEventCabinetStatsItemDto.builder()
                    .cabinetId(cabinetId)
                    .cabinetName(cabinetName)
                    .count(count != null ? count : 0L)
                    .build());
        }
        long total = byCabinet.stream().mapToLong(OzonApiEventCabinetStatsItemDto::count).sum();
        return OzonApiEventCabinetStatsDto.builder()
                .baseStatus(status != null ? status.name() : null)
                .baseEventType(eventType != null ? eventType.name() : null)
                .total(total)
                .byCabinet(byCabinet)
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<OzonApiEventDto> getEventsPage(
            int page,
            int size,
            OzonApiEventStatus status,
            OzonApiEventType eventType,
            Long cabinetId,
            OzonApiEventSortField sortBy,
            Sort.Direction sortDir
    ) {
        Sort sort = sortForAdminEvents(sortBy, sortDir);
        Pageable pageable = PageRequest.of(page, Math.clamp(size, 1, 100), sort);
        Page<OzonApiEvent> eventsPage = eventRepository.findAdminEvents(status, eventType, cabinetId, pageable);
        List<OzonApiEventDto> content = eventsPage.getContent().stream().map(this::toDto).toList();
        return PageResponse.<OzonApiEventDto>builder()
                .content(content)
                .totalElements(eventsPage.getTotalElements())
                .totalPages(eventsPage.getTotalPages())
                .size(eventsPage.getSize())
                .number(eventsPage.getNumber())
                .build();
    }

    @Transactional(readOnly = true)
    public OzonApiEventDto getEventById(Long eventId) {
        OzonApiEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Событие не найдено: " + eventId));
        return toDto(event);
    }

    @Transactional
    public void retryNow(Long eventId) {
        OzonApiEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Событие не найдено: " + eventId));
        event.setStatus(OzonApiEventStatus.CREATED);
        event.setNextAttemptAt(LocalDateTime.now());
        event.setLastError(null);
        event.setFinishedAt(null);
        event.setUpdatedAt(LocalDateTime.now());
        eventRepository.save(event);
    }

    @Transactional
    public int retryAllFailedFinalNow() {
        return eventRepository.bulkRetryByStatus(
                OzonApiEventStatus.FAILED_FINAL,
                OzonApiEventStatus.CREATED,
                LocalDateTime.now()
        );
    }

    @Transactional
    public void cancel(Long eventId) {
        OzonApiEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Событие не найдено: " + eventId));
        event.setStatus(OzonApiEventStatus.CANCELLED);
        event.setFinishedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        eventRepository.save(event);
    }

    /**
     * После остановки JVM события могли остаться в RUNNING.
     */
    @Transactional
    public int recoverRunningEventsAfterJvmStop() {
        List<OzonApiEvent> running = eventRepository.findByStatus(OzonApiEventStatus.RUNNING);
        if (running.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        for (OzonApiEvent event : running) {
            event.setStatus(OzonApiEventStatus.FAILED_RETRYABLE);
            event.setStartedAt(null);
            event.setLastError("Запуск приложения: сброс RUNNING после остановки процесса");
            event.setNextAttemptAt(now);
            event.setUpdatedAt(now);
        }
        eventRepository.saveAll(running);
        return running.size();
    }

    private static Sort sortForAdminEvents(OzonApiEventSortField sortBy, Sort.Direction sortDir) {
        OzonApiEventSortField effectiveSortBy = sortBy != null ? sortBy : OzonApiEventSortField.ID;
        Sort.Direction effectiveDir = sortDir != null ? sortDir : Sort.Direction.DESC;
        Order order = new Order(effectiveDir, effectiveSortBy.getFieldPath());
        if (effectiveSortBy == OzonApiEventSortField.STARTED_AT
                || effectiveSortBy == OzonApiEventSortField.FINISHED_AT
                || effectiveSortBy == OzonApiEventSortField.NEXT_ATTEMPT_AT) {
            order = order.nullsLast();
        }
        return Sort.by(order);
    }

    private OzonApiEventDto toDto(OzonApiEvent event) {
        return OzonApiEventDto.builder()
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
