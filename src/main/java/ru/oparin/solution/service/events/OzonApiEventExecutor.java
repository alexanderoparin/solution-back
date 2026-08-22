package ru.oparin.solution.service.events;

import ru.oparin.solution.model.OzonApiEvent;

/**
 * Исполнитель события Ozon API.
 */
public interface OzonApiEventExecutor {
    OzonApiEventExecutionResult execute(OzonApiEvent event);
}
