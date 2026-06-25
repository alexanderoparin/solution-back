package ru.oparin.solution.util;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Парсинг дат и времени из ответов WB API.
 */
@Slf4j
public final class WbDateTimeParser {

    private WbDateTimeParser() {
    }

    /**
     * Парсит ISO-дату WB (UTC с суффиксом Z или с offset, например +03:00).
     */
    public static LocalDateTime parse(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            return null;
        }
        try {
            if (dateTimeStr.endsWith("Z") || dateTimeStr.endsWith("z")) {
                return Instant.parse(dateTimeStr).atZone(ZoneOffset.UTC).toLocalDateTime();
            }
            return OffsetDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
        } catch (DateTimeParseException e) {
            log.warn("Не удалось распарсить дату WB '{}': {}", dateTimeStr, e.getMessage());
            return null;
        }
    }
}
