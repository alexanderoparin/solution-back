package ru.oparin.solution.service.events.enqueue;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.WbApiEventType;
import ru.oparin.solution.service.events.WbApiEventExecutors;
import ru.oparin.solution.service.events.WbApiEventWriter;
import ru.oparin.solution.service.events.payload.WbContentCardsListPagePayload;

import java.time.LocalDate;

/**
 * Постановка событий выгрузки карточек WB (content cards list).
 */
@Service
@RequiredArgsConstructor
public class WbContentEventEnqueue {

    private static final int MAX_ATTEMPTS = 3;
    private static final int PRIORITY = 100;

    private final WbApiEventWriter writer;

    /**
     * Первая страница карточек кабинета за период.
     */
    @Transactional
    public void enqueueInitialContentEvent(
            Long cabinetId,
            LocalDate dateFrom,
            LocalDate dateTo,
            boolean includeStocks,
            String triggerSource
    ) {
        WbContentCardsListPagePayload payload = WbContentCardsListPagePayload.builder()
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .includeStocks(includeStocks)
                .build();
        enqueueContentEvent(cabinetId, payload, buildContentDedupKey(cabinetId, null, null, dateFrom, dateTo), triggerSource);
    }

    /**
     * Следующая страница карточек по курсору.
     */
    @Transactional
    public void enqueueNextContentEvent(Long cabinetId, WbContentCardsListPagePayload payload, String triggerSource) {
        String dedupKey = buildContentDedupKey(
                cabinetId,
                payload.cursorNmId(),
                payload.cursorUpdatedAt(),
                payload.dateFrom(),
                payload.dateTo()
        );
        enqueueContentEvent(cabinetId, payload, dedupKey, triggerSource);
    }

    private void enqueueContentEvent(
            Long cabinetId,
            WbContentCardsListPagePayload payload,
            String dedupKey,
            String triggerSource
    ) {
        writer.insertIfAbsent(
                cabinetId,
                WbApiEventType.CONTENT_CARDS_LIST_PAGE,
                WbApiEventExecutors.CONTENT,
                payload,
                dedupKey,
                MAX_ATTEMPTS,
                PRIORITY,
                triggerSource,
                null
        );
    }

    private static String buildContentDedupKey(
            Long cabinetId,
            Long cursorNmId,
            String cursorUpdatedAt,
            LocalDate from,
            LocalDate to
    ) {
        return "CONTENT_CARDS_LIST_PAGE:"
                + cabinetId + ":"
                + (cursorNmId == null ? "first" : cursorNmId) + ":"
                + (cursorUpdatedAt == null ? "null" : cursorUpdatedAt) + ":"
                + from + ":" + to;
    }
}
