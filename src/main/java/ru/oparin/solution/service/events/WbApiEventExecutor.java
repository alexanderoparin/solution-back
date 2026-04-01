package ru.oparin.solution.service.events;

import ru.oparin.solution.model.WbApiEvent;

public interface WbApiEventExecutor {
    WbApiEventExecutionResult execute(WbApiEvent event);
}
