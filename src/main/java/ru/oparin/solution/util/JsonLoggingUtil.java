package ru.oparin.solution.util;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Утилитный класс для преобразования объектов в JSON строку с красивым форматированием.
 */
public class JsonLoggingUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Преобразует объект в JSON строку с красивым форматированием.
     *
     * @param object объект для сериализации в JSON
     * @return JSON строка с форматированием или строковое представление объекта при ошибке
     */
    public static String toPrettyJson(Object object) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (Exception e) {
            return object != null ? object.toString() : "null";
        }
    }
}

