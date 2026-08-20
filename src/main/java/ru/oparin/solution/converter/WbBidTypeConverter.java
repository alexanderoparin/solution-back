package ru.oparin.solution.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ru.oparin.solution.model.WbBidType;

/**
 * Конвертер для автоматического преобразования между {@link WbBidType} и {@link Integer}
 * при сохранении в базу данных.
 */
@Converter(autoApply = true)
public class WbBidTypeConverter implements AttributeConverter<WbBidType, Integer> {

    @Override
    public Integer convertToDatabaseColumn(WbBidType attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public WbBidType convertToEntityAttribute(Integer dbData) {
        return WbBidType.fromCode(dbData);
    }
}

