package ru.oparin.solution.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ru.oparin.solution.model.BidType;

/**
 * Конвертер для автоматического преобразования между {@link BidType} и {@link Integer}
 * при сохранении в базу данных.
 */
@Converter(autoApply = true)
public class BidTypeConverter implements AttributeConverter<BidType, Integer> {

    @Override
    public Integer convertToDatabaseColumn(BidType attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public BidType convertToEntityAttribute(Integer dbData) {
        return BidType.fromCode(dbData);
    }
}

