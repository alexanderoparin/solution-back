package ru.oparin.solution.model.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ru.oparin.solution.model.CabinetAccessSection;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA-конвертер списка разделов доступа в JSONB.
 */
@Converter
public class CabinetAccessSectionsConverter implements AttributeConverter<List<CabinetAccessSection>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<CabinetAccessSection>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<CabinetAccessSection> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Не удалось сериализовать разделы доступа", e);
        }
    }

    @Override
    public List<CabinetAccessSection> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(dbData, TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Не удалось десериализовать разделы доступа", e);
        }
    }
}
