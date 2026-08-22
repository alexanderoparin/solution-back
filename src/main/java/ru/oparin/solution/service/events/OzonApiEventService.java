package ru.oparin.solution.service.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.config.OzonEventsProperties;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.OzonApiEvent;
import ru.oparin.solution.model.OzonApiEventStatus;
import ru.oparin.solution.model.OzonApiEventType;
import ru.oparin.solution.repository.CabinetRepository;
import ru.oparin.solution.repository.OzonApiEventRepository;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.events.payload.OzonProductListPagePayload;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OzonApiEventService {

    public static final String PRODUCT_LIST_EXECUTOR_BEAN = "ozonProductListPageEventExecutor";

    private static final int PRODUCT_LIST_MAX_ATTEMPTS = 3;
    private static final int PRODUCT_LIST_PRIORITY = 100;

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
}
