package ru.oparin.solution.service.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.model.WbApiEventStatus;
import ru.oparin.solution.model.WbApiEventType;
import ru.oparin.solution.repository.CabinetRepository;
import ru.oparin.solution.repository.WbApiEventRepository;
import ru.oparin.solution.service.WbProductCardService;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

/**
 * Постановка WB API события в очередь: дедуп по активным статусам и сериализация payload.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WbApiEventWriter {

    public static final int PRIORITY_CARD_EVENT_BOOST = 1000;

    public static final Set<WbApiEventStatus> ACTIVE_STATUSES = Set.of(
            WbApiEventStatus.CREATED,
            WbApiEventStatus.RUNNING,
            WbApiEventStatus.FAILED_RETRYABLE,
            WbApiEventStatus.DEFERRED_RATE_LIMIT
    );

    private final WbApiEventRepository eventRepository;
    private final CabinetRepository cabinetRepository;
    private final WbProductCardService productCardService;
    private final ObjectMapper objectMapper;

    /**
     * {@code true}, если уже есть активное событие с этим {@code dedupKey}.
     */
    public boolean existsActive(String dedupKey) {
        return eventRepository.existsByDedupKeyAndStatusIn(dedupKey, ACTIVE_STATUSES);
    }

    /**
     * Кабинет по id или ошибка, если не найден.
     */
    public Cabinet requireCabinet(Long cabinetId) {
        return cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден: " + cabinetId));
    }

    /**
     * Создаёт событие, если нет активного с тем же {@code dedupKey}.
     *
     * @return сохранённое событие или empty, если постановка пропущена
     */
    public Optional<WbApiEvent> insertIfAbsent(
            Long cabinetId,
            WbApiEventType eventType,
            String executorBeanName,
            Object payload,
            String dedupKey,
            int maxAttempts,
            int priority,
            String triggerSource,
            LocalDateTime nextAttemptAt
    ) {
        Cabinet cabinet = requireCabinet(cabinetId);
        return insertIfAbsent(
                cabinet,
                eventType,
                executorBeanName,
                payload,
                dedupKey,
                maxAttempts,
                priority,
                triggerSource,
                nextAttemptAt
        );
    }

    /**
     * Создаёт событие для уже загруженного кабинета, если нет активного с тем же {@code dedupKey}.
     */
    public Optional<WbApiEvent> insertIfAbsent(
            Cabinet cabinet,
            WbApiEventType eventType,
            String executorBeanName,
            Object payload,
            String dedupKey,
            int maxAttempts,
            int priority,
            String triggerSource,
            LocalDateTime nextAttemptAt
    ) {
        if (existsActive(dedupKey)) {
            log.debug("WB API event уже существует (dedupKey={}), создание пропущено", dedupKey);
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime attemptAt = nextAttemptAt != null ? nextAttemptAt : now;
        WbApiEvent event = WbApiEvent.builder()
                .eventType(eventType)
                .status(WbApiEventStatus.CREATED)
                .executorBeanName(executorBeanName)
                .cabinet(cabinet)
                .payloadJson(writePayload(payload))
                .dedupKey(dedupKey)
                .attemptCount(0)
                .maxAttempts(maxAttempts)
                .nextAttemptAt(attemptAt)
                .priority(priority)
                .triggerSource(triggerSource)
                .createdAt(now)
                .updatedAt(now)
                .build();
        WbApiEvent saved = eventRepository.save(event);
        log.debug("Событие с id={} сохранено/обновлено", saved.getId());
        return Optional.of(saved);
    }

    /**
     * Приоритет карточки: +{@link #PRIORITY_CARD_EVENT_BOOST}, если артикул помечен приоритетным.
     */
    public int resolveNmIdEventPriority(Long cabinetId, Long nmId, int basePriority) {
        if (cabinetId == null || nmId == null) {
            return basePriority;
        }
        return productCardService.findByNmIdAndCabinetId(nmId, cabinetId)
                .map(card -> Boolean.TRUE.equals(card.getIsPriority())
                        ? basePriority + PRIORITY_CARD_EVENT_BOOST
                        : basePriority)
                .orElse(basePriority);
    }

    /**
     * Читает JSON payload события в тип {@code payloadType}.
     */
    public <T> T readPayload(WbApiEvent event, Class<T> payloadType) {
        try {
            return objectMapper.readValue(event.getPayloadJson(), payloadType);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Некорректный payload события " + event.getId() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Сериализует payload события в JSON.
     */
    public String writePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Не удалось сериализовать payload события: " + e.getMessage(), e);
        }
    }
}
